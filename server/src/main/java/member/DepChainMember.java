package member;

import java.io.IOException;
import java.net.DatagramSocket;
import java.util.Base64;
import java.util.List;

import com.google.gson.Gson;

import config.MemberConfig;
import consensus.QCManager;
import crypto.CryptoLib;
import info.ReplicaInfo;
import model.CatchUp;
import model.ClientRequest;
import model.GsonUtils;
import model.Message;
import model.Node;
import model.NodeTree;
import model.QC;
import network.DeliveryListener;
import network.NetworkLayerLib;
import network.UdpReceiver;
import util.DepChainUtil;
public class DepChainMember implements DeliveryListener{
    private NetworkLayerLib networkLayerLib;
    private int curView;          // current view number
    private QC lockedQC;         // the highest QC for which this replica voted "commit"
    private QC prepareQC;        // the highest QC for which this replica voted "pre-commit"

    private DatagramSocket socket;
    private UdpReceiver receiver;
    private NodeTree nodeTree;

    private final MemberConfig memberConfig; // All replica information
    private final QCManager qcManager;
    private final DepChainUtil util;
    private Node currentProposal; // Track leader's proposal
    private int lastExecutedHeight; // Track last executed command height
    private int timeoutCount = 1; // Track number of timeouts for exponential backoff

    private static final long PHASE_TIMEOUT_MS = 200;
    private Thread timeoutThread;

    public DepChainMember(MemberConfig memberConfig, DatagramSocket socket) {
        this.memberConfig = memberConfig;
        this.socket = socket;

        // Initialize consensus state
        this.nodeTree = new NodeTree();
        this.curView = 0;
        this.lockedQC = null;
        this.prepareQC = null;
        this.networkLayerLib = new NetworkLayerLib(this, socket);
        this.lastExecutedHeight = 0;

        // Initialize QC management
        this.qcManager = new QCManager(memberConfig);
        this.util = new DepChainUtil(qcManager);

    }


    public boolean safeNode(Node node, QC qc) {
        if (lockedQC == null) {
            return nodeTree.extendsFrom(node, qc.node);
        }
        //System.out.println("======= lockedQC node: " + lockedQC.node + " view: " + lockedQC.viewNumber);
        //System.out.println("======= justify node: " + qc.node + " view: " + qc.viewNumber);
        // Safety (Extends from lockedQC.node) Liveness (QC has higher view than locked QC)
        return nodeTree.extendsFrom(node, lockedQC.node) || qc.viewNumber > lockedQC.viewNumber;
    }

    public void setNetworkLayerLib(NetworkLayerLib networkLayerLib) {
        this.networkLayerLib = networkLayerLib;
    }

    @Override
    public void onDeliver(int senderPort, String message) {

        //System.out.println("--------------------------------");
        //System.out.println("Member received message from sender " + senderPort + ": " + message);
        //System.out.println("--------------------------------\n");
        String payload = message;
        if (payload.startsWith("SEQ=")) {
            int idx = payload.indexOf(' ');
            if (idx != -1) {
                payload = payload.substring(idx + 1);
            }
        }
        if (payload.startsWith("NewCommand=")) {
            int sigIndex = payload.indexOf("SIG=");
            if (sigIndex == -1) {
                System.err.println("No signature found for client command in message, ignoring");
                return;
            }
            String request = payload.substring("NewCommand=".length(), sigIndex).trim();
            System.out.println("Received new command: " + request);
            String sigB64 = payload.substring(sigIndex + 4).trim();
            byte[] sig;
            try {
                sig = Base64.getDecoder().decode(sigB64);
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid Base64 signature: " + sigB64);
                return;
            }
            
            if(senderPort < 4000){
                System.err.println("Command received is not from a client, ignoring");
                return;
            }
            String[] parts = request.split(":");
            int seq = Integer.parseInt(parts[0]);
            String command = parts[1];

            ClientRequest clientCmd = new ClientRequest(seq, senderPort, command, sig);
            memberConfig.addPendingCommand(clientCmd);
            return; 
        }

        if (payload.startsWith("CATCH-UP=")) {
            String json = payload.substring("CATCH-UP=".length()).trim();
            CatchUp m = GsonUtils.GSON.fromJson(json, CatchUp.class);
            handleCatchUpRequest(m);
            return;
        }

        if (payload.startsWith("CATCH-UP_RESPONSE=")) {
            String json = payload.substring("CATCH-UP_RESPONSE=".length()).trim();
            CatchUp m = GsonUtils.GSON.fromJson(json, CatchUp.class);
            handleCatchUpResponse(m);
            return;
        }

        Gson gson = GsonUtils.GSON;
        Message m = gson.fromJson(payload, Message.class);
        m.senderPort = senderPort; // Ensure senderPort is set

        switch (m.type) {
            case "new-view":
                if(m.viewNumber >= curView){ // the case where where i m the next leader and vote for me
                    if (qcManager.addVote(m)) {
                        handlePrepare();
                        startTimeout(); // prepare phase
                    }
                }
                break;
            case "prepare":
                if (memberConfig.isLeader(curView)) {
                    if (qcManager.addVote(m)) {
                        handlePreCommitLeader();
                        startTimeout(); // pre-commit phase
                    }
                } else {
                    handlePrepareReplica(m);
                }
                break;
            case "pre-commit":
                if (memberConfig.isLeader(curView)) {
                    if (qcManager.addVote(m)) {
                        handleCommitLeader();
                        startTimeout(); // commit phase
                    }
                } else {
                    handlePreCommitReplica(m);
                }
                break;
            case "commit":
                if (memberConfig.isLeader(curView)) {
                    if (qcManager.addVote(m)) {
                        handleDecideLeader();
                        proposeNewView();
                    }
                } else {
                    handleCommitReplica(m);
                }
                break;
            case "decide":
                handleDecideReplica(m);
                proposeNewView();
                break;
            default:
                throw new AssertionError("Unknown message type: " + m.type);
        }
    }
    
    public void start() {
        System.out.println("SERVER STARTED: ID=" + memberConfig.getID());
        this.receiver = new UdpReceiver(socket, networkLayerLib);
        new Thread(receiver).start();
        startTimeout(); // restart for new-view phase
        prepareQC = new QC("prepare", 0, nodeTree.getFirstNode(), null); // Initialize prepareQC to genesis QC
    }

    private void sendMessage(Message m, String destIp, int destPort) throws java.io.IOException {
        String json = GsonUtils.GSON.toJson(m);
        networkLayerLib.alpSend(json, destIp, destPort);
    }

    private void broadcast(Message m) throws java.io.IOException {
        for (ReplicaInfo replica : memberConfig.getAllReplicas()) {
            sendMessage(m, replica.getIP(), replica.getPort());
        }
    }

    private void handlePrepare(){
        QC maxQC = getMaxQC();  // Read new-view votes first
        
        if (maxQC.viewNumber > prepareQC.viewNumber) {
            System.out.println("Received a maxQC bigger then my prepareQC" );
            sendCatchUpRequest(maxQC.node);
        }

        // Clear new-view votes to prevent multiple calls to handlePrepare
        qcManager.clearVotesForTypeView("new-view", curView);
        try {
            Node curProposal;

            if (memberConfig.getPendingCommands().isEmpty()) {
                System.out.println("No pending client commands, proposing NO-OP");
                ClientRequest noOpCmd = new ClientRequest(-1, -1, "NO-OP",null);
                curProposal = Node.createLeaf(maxQC.node, noOpCmd);
            } else {
                System.out.println("Processing pending client command " + memberConfig.getPendingCommands());
                ClientRequest cmd = memberConfig.getPendingCommands().iterator().next();
                curProposal = Node.createLeaf(maxQC.node, cmd);
            }

            this.currentProposal = curProposal; // Store for QC formation

            Message prepareMsg = util.voteMsg("prepare", curProposal, maxQC, curView);
            prepareMsg.senderPort = memberConfig.getID() + 3000;
            qcManager.addVote(prepareMsg);
            broadcast(prepareMsg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    
    private void handlePrepareReplica(Message m) {
        try {
            // Verify the justify QC if present

            if (m.justify == null) {
                System.err.println("Null new-view maxQC in prepare message");
                proposeNewView();
                return;
            }
            byte[] sig = m.node.cmd.getSig();
            String command = m.node.cmd.getCommand();
            int seq = m.node.cmd.getSeq();
            int senderId = m.node.cmd.getPort() - 4000;
            String PUBLIC_KEY_PATH = "../rsa_keys/client_" + senderId + "/client_" + senderId + ".pubkey";

            String fullCommand =  seq + ":" + command; // reconstruct full command for signature verification
            if(!command.equals("NO-OP") && !CryptoLib.verifySignature(fullCommand.getBytes(), sig, PUBLIC_KEY_PATH)){
                System.err.println("Invalid signature for client command in prepare message, ignoring");
                proposeNewView();
                return;
            }

            if(!command.equals("NO-OP") && memberConfig.isDuplicateRequest(m.node.cmd)) {
                System.err.println("Duplicate client command in prepare message, ignoring");
                proposeNewView();
                return;
            }

            int matchResult = util.matchingMsg(m, curView); // 0 lower view, 1 exact match, 2 same type higher view

            if (matchResult == 0 || !cameFromLeader(m, m.viewNumber)) {
                System.err.println("Invalid late message or sender in prepare message");
                return;
            }

            if (curView != 0 && !qcManager.verifyQC(m.justify)) {
                System.err.println("Invalid justify QC in prepare message");
                proposeNewView();
                return;
            }
            
            if (matchResult == 2) {
                System.out.println("Received higher view prepare message, updating view to " + m.viewNumber);
                sendCatchUpRequest(m.justify.node);
                curView = m.viewNumber; // Update to higher view
            }

            if (nodeTree.extendsFrom(m.node, m.justify.node) && safeNode(m.node, m.justify)) {
                startTimeout();
                Message voteMsg = util.voteMsg("prepare", m.node, null, curView);
                voteMsg.senderPort = memberConfig.getID() + 3000;

                int leader = memberConfig.getLeader(curView);
                ReplicaInfo replica = memberConfig.getReplicaInfo(leader);
                sendMessage(voteMsg, replica.getIP(), replica.getPort());
            } else {
                System.err.println("Node failed safety check in prepare message");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void handlePreCommitLeader() {
        try {
            prepareQC = qcManager.formQC("prepare", curView, currentProposal);
            nodeTree.storeNode(currentProposal);
            Message preCommitMsg = util.voteMsg("pre-commit", currentProposal, prepareQC, curView);
            preCommitMsg.senderPort = memberConfig.getID() + 3000;
            qcManager.addVote(preCommitMsg);
            broadcast(preCommitMsg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handlePreCommitReplica(Message m) {
        try {
            // Verify the prepareQC
            if (m.justify == null) {
                System.err.println("Null prepareQC in pre-commit message");
                proposeNewView();
                return;
            }

            int matchResult = util.matchingQC(m.justify, "prepare", curView); // 0 no match, 1 exact match, 2 same type higher view

            if (matchResult == 0 || !cameFromLeader(m, m.viewNumber)) {
                System.err.println("Invalid late prepareQC or sender in pre-commit message");
                return;
            }

            if (!qcManager.verifyQC(m.justify)){
                System.err.println("Invalid prepareQC in pre-commit message");
                System.out.println("justify view: " + m.justify.viewNumber + " current view: " + curView);
                proposeNewView();
                return;
            }

            if (matchResult == 2) {
                System.out.println("Received higher prepare message, updating view to " + m.viewNumber);
                sendCatchUpRequest(m.justify.node);
                curView = m.viewNumber; // Update to higher view
            }

            startTimeout();

            nodeTree.storeNode(m.node);
            prepareQC = m.justify;

            Message voteMsg = util.voteMsg("pre-commit", m.justify.node, null, curView);
            voteMsg.senderPort = memberConfig.getID() + 3000;

            int leader = memberConfig.getLeader(curView);
            ReplicaInfo replica = memberConfig.getReplicaInfo(leader);
            sendMessage(voteMsg, replica.getIP(), replica.getPort());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleCommitLeader() {
        try {
            lockedQC = qcManager.formQC("pre-commit", curView, currentProposal);

            Message commitMsg = util.voteMsg("commit", currentProposal, lockedQC, curView);
            commitMsg.senderPort = memberConfig.getID() + 3000;
            qcManager.addVote(commitMsg);
            broadcast(commitMsg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
  


    private void handleCommitReplica(Message m) {
        try {
            // Verify the precommitQC

            if (m.justify == null) {
                System.err.println("Null precommitQC in commit message");
                proposeNewView();
                return;
            }

            int matchResult = util.matchingQC(m.justify, "pre-commit", curView); // 0 no match, 1 exact match, 2 same type higher view

            if (matchResult == 0 || !cameFromLeader(m, m.viewNumber)) {
                System.err.println("Invalid late precommitQC or sender in commit message");
                return;
            }

            if (!qcManager.verifyQC(m.justify)) {
                System.err.println("Invalid precommitQC in commit message");
                proposeNewView();
                return;
            }

            if (matchResult == 2) {
                System.out.println("Received higher view pre-commit message, updating view to " + m.viewNumber);
                sendCatchUpRequest(m.justify.node);
                curView = m.viewNumber; // Update to higher view
            }
            startTimeout();

            lockedQC = m.justify;

            Message voteMsg = util.voteMsg("commit", m.justify.node, null, curView);
            voteMsg.senderPort = memberConfig.getID() + 3000;

            int leader = memberConfig.getLeader(curView);
            ReplicaInfo replica = memberConfig.getReplicaInfo(leader);
            sendMessage(voteMsg, replica.getIP(), replica.getPort());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleDecideLeader() {
        try {
            QC commitQC = qcManager.formQC("commit", curView, currentProposal);

            Message decideMsg = DepChainUtil.Msg("decide", currentProposal, commitQC, curView);
            decideMsg.senderPort = memberConfig.getID() + 3000;

            broadcast(decideMsg);
            executeNode(commitQC.node);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleDecideReplica(Message m) {
        if (m.justify == null) {
            System.err.println("Null commitQC in commit message");
            proposeNewView();
            return;
        }

        int matchResult = util.matchingQC(m.justify, "commit", curView); // 0 no match, 1 exact match, 2 same type higher view

        if (matchResult == 0 || !cameFromLeader(m, m.viewNumber)) {
            System.err.println("Invalid late commitQC or sender in commit message");
            return;
        }

        if (!qcManager.verifyQC(m.justify)) {
            System.err.println("Invalid commitQC in commit message");
            proposeNewView();
            return;
        }

        if (matchResult == 2) {
            System.out.println("Received higher view commit message, updating view to " + m.viewNumber);
            sendCatchUpRequest(m.justify.node);
            curView = m.viewNumber; // Update to higher view
        }

        startTimeout();
        executeNode(m.justify.node);
    }

    private QC getMaxQC() { //maybe put this in utils
        List<Message> msgs = qcManager.getVotes("new-view", curView);
        QC maxqc = null;
        for (Message m : msgs) {
            if (m != null && m.justify != null) {
                if (maxqc == null || m.justify.viewNumber > maxqc.viewNumber) {
                    maxqc = m.justify;
                }
            }
        }
        return maxqc;
    }

    private boolean cameFromLeader(Message m, int viewNumber) {
        return (m.senderPort - 3000) == memberConfig.getLeader(viewNumber);
    }

    // Timeout logic
    
    private void startTimeout() {
        stopTimeout();
        timeoutThread = new Thread(() -> {
            try {
                System.err.println("Starting timeout for view " + curView + " with timeout count " + timeoutCount);
                Thread.sleep(PHASE_TIMEOUT_MS * timeoutCount);
                System.out.println("timeout reached. Proposing new view.");
                if (timeoutCount < 64) {
                    timeoutCount *= 2;
                }
                proposeNewView();
            } catch (InterruptedException ignored) { timeoutCount = 1; } //reset timeout
        });
        timeoutThread.start();
    }

    private void stopTimeout() {
        if (timeoutThread != null) {
            timeoutThread.interrupt();
            timeoutThread = null;
        }
    }

    private void proposeNewView() {
        stopTimeout();
        curView++;
        System.out.println("Proposing new view: " + curView);
        Message newViewMsg = DepChainUtil.Msg("new-view", null, prepareQC, curView);
        newViewMsg.senderPort = memberConfig.getID() + 3000;
        int leaderID = memberConfig.getLeader(curView);
        ReplicaInfo replica = memberConfig.getReplicaInfo(leaderID);
        if (replica == null) {
            qcManager.addVote(newViewMsg);
            return;
        }
        try {
            sendMessage(newViewMsg, replica.getIP(), replica.getPort());
        } catch (IOException e) {
            e.printStackTrace();
        }
        startTimeout(); // restart for new-view phase
    }
    
    private void executeNode(Node node) {
        List<Node> cmdToExecute = nodeTree.getNodesUntil(node, lastExecutedHeight);
        
        lastExecutedHeight = node.height;

        for (int i = cmdToExecute.size() - 1; i >= 0; i--) { // Execute from lowest height to highest
            Node n = cmdToExecute.get(i);
            if (n.cmd.getCommand().equals("NO-OP")) {
                System.out.println("Executed NO-OP at height " + n.height);
                try {
                    Thread.sleep(3000); // FIXME this is not perfect
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                if (memberConfig.isDuplicateRequest(n.cmd)) {
                    System.out.println("Skipping duplicate command at height " + n.height + ": " + n.cmd.getCommand());
                    continue;
                }
                memberConfig.setLastSequenceForClient(n.cmd.getPort(), n.cmd.getSeq());
                System.out.println("Executed command at height " + n.height + ": " + n.cmd.getCommand() + " sending back to client " + n.cmd.getPort());
                memberConfig.addToAppState(n.cmd.getCommand());
                memberConfig.removePendingCommand(n.cmd);
                System.out.println("Current app state: " + memberConfig.getAppState());
                String message = "DECIDED= " + n.cmd.getCommand();
                try {
                    networkLayerLib.alpSend(message, "localhost", n.cmd.getPort());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendCatchUpRequest(Node receivedNode){
        String message = "CATCH-UP= "; // we send the whole but only need the height of the lockedQC node to verify
        CatchUp m = new CatchUp();
        m.viewNumber = curView;
        m.lockedQC = lockedQC;
        m.receivedNode = receivedNode; // the node that the sender of the catch-up message is currently at (the one he sent me in justify)
        m.senderPort = memberConfig.getID() + 3000;
        String json = GsonUtils.GSON.toJson(m);
        message += json;
        
        try {
            networkLayerLib.alpSend(message, "localhost", m.senderPort);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void handleCatchUpRequest(CatchUp msg){
        System.out.println("Received catch-up message with lockedQC: " + msg.lockedQC + " from sender " + msg.senderPort);
        
        if (msg.lockedQC == null || !qcManager.verifyQC(msg.lockedQC)) {
            System.err.println("Invalid QC in catch-up message");
            return;
        }
        List<Node> nodesToCatchUp = nodeTree.getNodesUntil(msg.receivedNode, msg.lockedQC.node.height);
       
        String message = "CATCH-UP_RESPONSE= ";

        CatchUp response = new CatchUp();
        response.viewNumber = curView;
        response.nodeList = nodesToCatchUp;
        response.senderPort = memberConfig.getID() + 3000;

        String json = GsonUtils.GSON.toJson(response);
        message += json;
        
        try {
            networkLayerLib.alpSend(message, "localhost", msg.senderPort);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void handleCatchUpResponse(CatchUp msg){
        List<Node> nodeList = msg.nodeList;
        for (int i = nodeList.size() - 1; i >= 0; i--) { // Execute from lowest height to highest
            Node n = nodeList.get(i);
            try {
                if (nodeTree.getNodeByHash(n.depHash()) == null) {
                    System.out.println("Storing catch-up node at height " + n.height + ": " + n);
                    nodeTree.storeNode(n);
                } else {
                    System.out.println("Node already exists in tree, skipping store for node at height " + n.height + ": " + n);
                }
            } catch (Exception e) {
                System.out.println("Error storing catch-up node: " + e.getMessage());
            }
        }
        System.out.println("Updated node tree with catch-up nodes, now at height " + nodeList.get(0).height);
    }

}


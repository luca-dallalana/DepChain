package member;

import java.io.IOException;
import java.net.DatagramSocket;

import com.google.gson.Gson;

import config.MemberConfig;
import consensus.QCManager;
import info.ReplicaInfo;
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

    private Message[] newViewLog;

    private static final long PHASE_TIMEOUT_MS = 60000; //FIXME this is set to 1 minute for testing, should be lower for real use
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

        // Initialize QC management
        this.qcManager = new QCManager(memberConfig);
        this.util = new DepChainUtil(qcManager);

    }


    public boolean safeNode(Node node, QC qc) {
        if (lockedQC == null) {
            return nodeTree.extendsFrom(node, qc.node);
        }
        // Safety (Extends from lockedQC.node) Liveness (QC has higher view than locked QC)
        return nodeTree.extendsFrom(node, lockedQC.node) || qc.viewNumber > lockedQC.viewNumber;
    }

    public void setNetworkLayerLib(NetworkLayerLib networkLayerLib) {
        this.networkLayerLib = networkLayerLib;
    }

    @Override
    public void onDeliver(int senderPort, String message) {

         System.out.println("--------------------------------");
        System.out.println("Member received message from sender " + senderPort + ": " + message);
         System.out.println("--------------------------------\n");
        String payload = message;
        if (payload.startsWith("SEQ=")) {
            int idx = payload.indexOf(' ');
            if (idx != -1) {
                payload = payload.substring(idx + 1);
            }
        }
        if (payload.startsWith("NewCommand=")) {
            String command = payload.substring("NewCommand=".length());
            System.out.println("Received new command: " + command);
            ClientRequest clientCmd = new ClientRequest(senderPort, command);
            memberConfig.addPendingCommand(clientCmd);
            if(curView == 0 && memberConfig.isLeader(curView)) {
                try {
                    QC qc = new QC("prepare", curView, nodeTree.getFirstNode(), null);
                    ClientRequest cmd = memberConfig.getPendingCommands().iterator().next();
                    Node curProposal = Node.createLeaf(qc.node, cmd);//FIXME add user cmd
                    this.currentProposal = curProposal; // Store for QC formation
                    Message prepareMsg = DepChainUtil.Msg("prepare", curProposal, qc, curView);
                    prepareMsg.senderPort = memberConfig.getID();
                    qcManager.addVote(prepareMsg);
                    //new Thread(() -> {
                        try {
                            broadcast(prepareMsg);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    //}).start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
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
        this.newViewLog = new Message[memberConfig.getN()];
        this.receiver = new UdpReceiver(socket, networkLayerLib);
        new Thread(receiver).start();
        startTimeout(); // restart for new-view phase
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
        QC maxQC = getMaxQC(newViewLog);
        try {
            Node curProposal = Node.createLeaf(maxQC.node, null);//FIXME add user cmd
            this.currentProposal = curProposal; // Store for QC formation

            Message prepareMsg = DepChainUtil.Msg("prepare", curProposal, maxQC, curView);
            prepareMsg.senderPort = memberConfig.getID();
            qcManager.addVote(prepareMsg);
            broadcast(prepareMsg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    
    private void handlePrepareReplica(Message m) {
        try {
            // Verify the justify QC if present
            if (!qcManager.verifyQC(m.justify)) {
                System.err.println("Invalid justify QC in prepare message");
                return;
            }

            if (safeNode(m.node, m.justify)) {
                Message voteMsg = util.voteMsg("prepare", m.node, null, curView);
                voteMsg.senderPort = memberConfig.getID();

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
            QC prepareQC = qcManager.formQC("prepare", curView, currentProposal);

            Message preCommitMsg = DepChainUtil.Msg("pre-commit", currentProposal, prepareQC, curView);
            preCommitMsg.senderPort = memberConfig.getID();
            qcManager.addVote(preCommitMsg);
            broadcast(preCommitMsg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handlePreCommitReplica(Message m) {
        try {
            // Verify the prepareQC
            if (m.justify == null || !qcManager.verifyQC(m.justify)) {
                System.err.println("Invalid prepareQC in pre-commit message");
                return;
            }

            prepareQC = m.justify;

            Message voteMsg = util.voteMsg("pre-commit", m.justify.node, null, curView);
            voteMsg.senderPort = memberConfig.getID();

            int leader = memberConfig.getLeader(curView);
            ReplicaInfo replica = memberConfig.getReplicaInfo(leader);
            sendMessage(voteMsg, replica.getIP(), replica.getPort());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleCommitLeader() {
        try {
            QC precommitQC = qcManager.formQC("pre-commit", curView, currentProposal);

            Message commitMsg = DepChainUtil.Msg("commit", currentProposal, precommitQC, curView);
            commitMsg.senderPort = memberConfig.getID();
            qcManager.addVote(commitMsg);
            broadcast(commitMsg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
  


    private void handleCommitReplica(Message m) {
        try {
            // Verify the precommitQC
            if (m.justify == null || !qcManager.verifyQC(m.justify)) {
                System.err.println("Invalid precommitQC in commit message");
                return;
            }

            lockedQC = m.justify;

            Message voteMsg = util.voteMsg("commit", m.justify.node, null, curView);
            voteMsg.senderPort = memberConfig.getID();

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
            decideMsg.senderPort = memberConfig.getID();

            broadcast(decideMsg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleDecideReplica(Message m) {
        //execute the command in m.justify.node
        System.out.println("Command decided: " + m.justify.node.cmd);
        memberConfig.removePendingCommand(m.justify.node.cmd);
        String message = "Decided command= " + m.justify.node.cmd;
        try {
            networkLayerLib.alpSend(message, "localhost", 5000); // Send execution result to central server
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private QC getMaxQC(Message[] msgs) { //maybe put this in utils
        QC maxqc = null;
        for (Message m : msgs) {
            if (m.justify != null) {
                if (maxqc == null || m.justify.viewNumber > maxqc.viewNumber) {
                    maxqc = m.justify;
                }
            }
        }
        return maxqc;
    }

    // Timeout logic
    
    private void startTimeout() {
        stopTimeout();
        timeoutThread = new Thread(() -> {
            try {
                Thread.sleep(PHASE_TIMEOUT_MS);
                System.out.println("timeout reached. Proposing new view.");
                proposeNewView();
            } catch (InterruptedException ignored) {}
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
        newViewMsg.senderPort = memberConfig.getID();
        try {
            broadcast(newViewMsg);
        } catch (IOException e) {
            e.printStackTrace();
        }
        startTimeout(); // restart for new-view phase
    }
}


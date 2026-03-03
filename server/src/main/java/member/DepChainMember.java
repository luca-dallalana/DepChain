package member;

import java.net.DatagramSocket;

import com.google.gson.Gson;

import config.MemberConfig;
import consensus.QCManager;
import model.GsonUtils;
import model.Message;
import model.Node;
import model.NodeTree;
import model.QC;
import network.DeliveryListener;
import network.NetworkLayerLib;
import network.UdpReceiver;
import util.DepChainUtil;
import config.ReplicaInfo;


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

    private int newViewCount;
    private int prepareCount;
    private int preCommitCount;
    private int commitCount;

    private Message[] newViewLog;

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
    public void onDeliver(int senderId, String message) {

        System.out.println("Member received message from sender " + senderId + ": " + message);
        String payload = message;
        if (payload.startsWith("SEQ=")) {
            int idx = payload.indexOf(' ');
            if (idx != -1) {
                payload = payload.substring(idx + 1);
            }
        }
        Gson gson = GsonUtils.GSON;
        Message m = gson.fromJson(payload, Message.class);
        m.senderId = senderId; // Ensure senderId is set

        switch (m.type) {
            case "new-view": //FIXME check non-leaders case ???
                if(m.viewNumber == curView){ // FIXME: paper says curview - 1
                    newViewLog[newViewCount] = m;
                    if (waitFor(0)) {
                        handlePrepare();
                    }
                }
                break;
            case "prepare":
                if (memberConfig.isLeader(curView)) {
                    qcManager.addVote(m); // Collect vote
                    if (waitFor(1)) {
                        handlePreCommitLeader();
                    }
                } else {
                    handlePrepareReplica(m);
                }
                break;
            case "pre-commit":
                if (memberConfig.isLeader(curView)) {
                    qcManager.addVote(m); // Collect vote
                    if (waitFor(2)) {
                        handleCommitLeader();
                    }
                } else {
                    handlePreCommitReplica(m);
                }
                break;
            case "commit":
                if (memberConfig.isLeader(curView)) {
                    qcManager.addVote(m); // Collect vote
                    if (waitFor(3)) {
                        handleDecideLeader();
                    }
                } else {
                    handleCommitReplica(m);
                }
                break;
            case "decide":
                handleDecideReplica(m);
                break;
            default:
                throw new AssertionError("Unknown message type: " + m.type);
        }
    }
    
    public void start() {
        System.out.println("SERVER STARTED: ID=" + memberConfig.getID());
        newViewCount = 0;
        prepareCount = 0;
        preCommitCount = 0;
        commitCount = 0;
        this.receiver = new UdpReceiver(socket, networkLayerLib);
        new Thread(receiver).start();
    }

    // FIXME: need to extend logic
    private void sendMessage(Message m, String destIp, int destPort) throws java.io.IOException {
        String json = GsonUtils.GSON.toJson(m);
        networkLayerLib.alpSend(json, destIp, destPort);
    }

    private void broadcast(Message m) throws java.io.IOException {
        for (ReplicaInfo replica : memberConfig.getAllReplicas()) {
            sendMessage(m, replica.getIP(), replica.getPort());
        }
    }

    private boolean waitFor(int phase) {
        switch (phase) {
            case 0:
                newViewCount++;
                if(newViewCount == memberConfig.getQuorumSize()){
                    newViewCount = 0;
                    return true;
                }
                return false;
            case 1:
                prepareCount++;
                if(prepareCount == memberConfig.getQuorumSize()){
                    prepareCount = 0;
                    return true;
                }
                return false;
            case 2:
                preCommitCount++;
                if(preCommitCount == memberConfig.getQuorumSize()){
                    preCommitCount = 0;
                    return true;
                }
                return false;
            case 3:
                commitCount++;
                if(commitCount == memberConfig.getQuorumSize()){
                    commitCount = 0;
                    return true;
                }
                return false;
            default:
                throw new AssertionError();
        }
    }

    private void handlePrepare(){
        QC maxQC = getMaxQC(newViewLog);
        try {
            Node curProposal = Node.createLeaf(maxQC.node, null);//FIXME add user cmd
            this.currentProposal = curProposal; // Store for QC formation

            Message prepareMsg = DepChainUtil.Msg("prepare", curProposal, maxQC, curView);
            prepareMsg.senderId = memberConfig.getID();

            broadcast(prepareMsg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    
    private void handlePrepareReplica(Message m) {
        try {
            // Verify the justify QC if present
            if (m.justify != null && !qcManager.verifyQC(m.justify)) {
                System.err.println("Invalid justify QC in prepare message");
                return;
            }

            if (safeNode(m.node, m.justify)) {
                Message voteMsg = util.voteMsg("prepare", m.node, null, curView);
                voteMsg.senderId = memberConfig.getID();

                int leader = memberConfig.getLeader(curView);
                ReplicaInfo replica = memberConfig.getReplicaInfo(leader);
                sendMessage(voteMsg, replica.getIP(), replica.getPort());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void handlePreCommitLeader() {
        try {
            QC prepareQC = qcManager.formQC("prepare", curView, currentProposal);

            Message preCommitMsg = DepChainUtil.Msg("pre-commit", currentProposal, prepareQC, curView);
            preCommitMsg.senderId = memberConfig.getID();

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
            voteMsg.senderId = memberConfig.getID();

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
            commitMsg.senderId = memberConfig.getID();

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
            voteMsg.senderId = memberConfig.getID();

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
            decideMsg.senderId = memberConfig.getID();

            broadcast(decideMsg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleDecideReplica(Message m) {
        //execute the command in m.justify.node
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
    
}

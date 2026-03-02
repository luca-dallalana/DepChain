package member;

import com.google.gson.Gson;

import config.MemberConfig;
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

    private UdpReceiver receiver;
    private NodeTree nodeTree;

    private final MemberConfig memberConfig; // All replica information

    private int newViewCount;
    private int prepareCount;
    private int preCommitCount;
    private int commitCount;

    private Message[] newViewLog;

    public DepChainMember(MemberConfig memberConfig, int localPort) {
        this.memberConfig = memberConfig;

        // Initialize consensus state
        this.nodeTree = new NodeTree();
        this.curView = 0;
        this.lockedQC = null;
        this.prepareQC = null;
        this.networkLayerLib = new NetworkLayerLib(this, localPort);

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
                    if (waitFor(1)) {
                        handlePreCommitLeader();
                    }
                } else {
                    handlePrepareReplica(m);
                }
                break;
            case "pre-commit":
                if (memberConfig.isLeader(curView)) {
                    if (waitFor(2)) {
                        handleCommitLeader();
                    }
                } else {
                    handlePreCommitReplica(m);
                }
                break;
            case "commit":
                if (memberConfig.isLeader(curView)) {
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
        this.receiver = new UdpReceiver(3000 + memberConfig.getID(), networkLayerLib);
        new Thread(receiver).start();
    }

    // FIXME: need to extend logic
    private void sendMessage(Message m, String destIp, int destPort, int seq) throws java.io.IOException {
        String json = GsonUtils.GSON.toJson(m);
        String packet = "SEQ=" + seq + " " + json;
        networkLayerLib.alpSend(packet, destIp, destPort, seq);
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
            Message prepareMsg = DepChainUtil.Msg("prepare", curProposal, maxQC, curView);
            for (ReplicaInfo replica : memberConfig.getAllReplicas()) {
                sendMessage(prepareMsg, replica.getIP(), replica.getPort(), 0); // FIXME: seq
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    
    private void handlePrepareReplica(Message m) {
        if (safeNode(m.node, m.justify)) {
            Message prepareMsg = DepChainUtil.voteMsg("prepare", m.node, null, curView); //FIXME is it null ?
            
            int leader = memberConfig.getLeader(curView);
            ReplicaInfo replica = memberConfig.getReplicaInfo(leader);
            try {
                sendMessage(prepareMsg, replica.getIP(), replica.getPort(), 0); // FIXME: seq
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    private void handlePreCommitLeader() {
        // Combine partial signatures into a prepareQC
        // Broadcast it Msg("pre-commit", null, prepareQC));
    }

    private void handlePreCommitReplica(Message m) {
        prepareQC = m.justify;

        Message prepareMsg = DepChainUtil.voteMsg("prepare", m.justify.node, null, curView); //FIXME is it null ?
        
        int leader = memberConfig.getLeader(curView);
        ReplicaInfo replica = memberConfig.getReplicaInfo(leader);
        try {
            sendMessage(prepareMsg, replica.getIP(), replica.getPort(), 0); // FIXME: seq
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }

    private void handleCommitLeader() {
        //combine partial signatures into a precommitQC
        //broadcast(Msg("commit", null, precommitQC));
    }
  


    private void handleCommitReplica(Message m) {
        lockedQC = m.justify;

        Message prepareMsg = DepChainUtil.voteMsg("commit", m.justify.node, null, curView); //FIXME is it null ?

        int leader = memberConfig.getLeader(curView);
        ReplicaInfo replica = memberConfig.getReplicaInfo(leader);
        try {
            sendMessage(prepareMsg, replica.getIP(), replica.getPort(), 0); //  FIXME: seq
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleDecideLeader() {
        //combine partial signatures into a commitQC
        //broadcast(Msg("commit", null, commitQC));
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
    /* 
            Message m = receiveMessage();
        if (matchingMsg(m, "new-view", curView)) {
            handleNewView(m);
        } else if (matchingMsg(m, "prepare", curView)) {
            handlePrepare(m);
        } else if (matchingMsg(m, "pre-commit", curView)) {
            handlePreCommit(m);
        } else if (matchingMsg(m, "commit", curView)) {
            handleCommit(m);
        } else if (matchingMsg(m, "decide", curView)) {
            handleDecide(m);
        }
            */
}

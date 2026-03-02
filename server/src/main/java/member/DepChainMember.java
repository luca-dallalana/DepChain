package member;

import config.MemberConfig;
import config.ReplicaInfo;
import model.Node;
import model.NodeTree;
import model.QC;
import network.DeliveryListener;
import network.NetworkLayerLib;
import network.UdpReceiver;


public class DepChainMember implements DeliveryListener{
    private NetworkLayerLib networkLayerLib;
    private int curView;          // current view number
    private QC lockedQC;         // the highest QC for which this replica voted "commit"
    private QC prepareQC;        // the highest QC for which this replica voted "pre-commit"

    private NodeTree nodeTree;

    private final MemberConfig memberConfig; // All replica information

    public DepChainMember(MemberConfig memberConfig) {
        this.memberConfig = memberConfig;

        // Initialize consensus state
        this.nodeTree = new NodeTree();
        this.curView = 0;
        this.lockedQC = null;
        this.prepareQC = null;
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
        //FIXME missing logic
        System.out.println("Member received message from sender " + senderId + ": " + message);
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

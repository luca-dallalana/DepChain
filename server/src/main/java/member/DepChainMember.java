package member;

import model.QC;
import network.DeliveryListener;
import network.NetworkLayerLib;
import network.UdpReceiver;


public class DepChainMember implements DeliveryListener{
    private NetworkLayerLib networkLayerLib;
    private UdpReceiver receiver;
    
    private int curView;          // current view number
    private QC lockedQC;         // the highest QC for which this replica voted "commit"
    private QC prepareQC;        // the highest QC for which this replica voted "pre-commit"

    public DepChainMember(int localPort, int listenerPort) { //FIXME let the config do this only send the lib even the receiver is not needed
        this.networkLayerLib = new NetworkLayerLib(this, localPort);
        this.receiver = new UdpReceiver(listenerPort, networkLayerLib);
        new Thread(receiver).start();
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

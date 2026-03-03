package central;
import java.io.IOException;

import network.DeliveryListener;
import network.NetworkLayerLib;
import network.UdpReceiver;

public class CentralApp implements DeliveryListener{

    private UdpReceiver receiver;
    private NetworkLayerLib networkLayerLib;
    private int localPort;
    private int numMembers;

    public CentralApp(int localPort, int numMembers) {
        this.localPort = localPort;
        this.numMembers = numMembers;
        this.networkLayerLib = new NetworkLayerLib(this, 4001); //FIXME: hardcoded port 

    }
    public void start() {
        System.out.println("===== Central SERVER STARTED =====");

        this.receiver = new UdpReceiver(this.localPort, this.networkLayerLib);
        new Thread(receiver).start();
    }

    @Override
    public void onDeliver(int senderId, String message) {
        if (message.startsWith("NewCommand=")) {
            try {
                sendMessage(message, "localhost", 3000, 7);
            } catch (IOException ex) {
            }
        }
    }

    private void sendMessage(String m, String destIp, int destPort, int seq) throws java.io.IOException {
        System.out.println("sending message to server: " + m);
        networkLayerLib.alpSend(m, destIp, destPort, seq); //FIXME: when to stop resending?
    }
}

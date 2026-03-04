package central;
import java.io.IOException;
import java.net.DatagramSocket;

import network.DeliveryListener;
import network.NetworkLayerLib;
import network.UdpReceiver;

public class CentralApp implements DeliveryListener{

    private UdpReceiver receiver;
    private NetworkLayerLib networkLayerLib;
    private int numMembers;
    private DatagramSocket socket;

    public CentralApp(int localPort, int numMembers, DatagramSocket socket) {
        this.numMembers = numMembers;
        this.socket = socket;
        this.networkLayerLib = new NetworkLayerLib(this, socket); 

    }
    public void start() {
        System.out.println("===== Central SERVER STARTED =====");

        this.receiver = new UdpReceiver(this.socket, this.networkLayerLib);
        new Thread(receiver).start();
    }

    @Override
    public void onDeliver(int senderId, String message) {
        System.out.println("Received: " + message);
        String payload = message;
        if (message.startsWith("SEQ=")) {
            int idx = message.indexOf(' ');
            if (idx != -1) {
                payload = message.substring(idx + 1);
            }
        }

        if (payload.startsWith("NewCommand=")) {
            try {
                sendMessage(payload, "localhost", 3000); // FIXME: eventually needs to be broadcast to all members
            } catch (IOException ex) {
                System.err.println("Failed to forward command: " + ex.getMessage());
            }
        }
    }

    private void sendMessage(String m, String destIp, int destPort) throws java.io.IOException {
        System.out.println("sending message to server: " + m);
        networkLayerLib.alpSend(m, destIp, destPort);
    }
}

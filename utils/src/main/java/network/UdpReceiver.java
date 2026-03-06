package network;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UdpReceiver implements Runnable{

    private static final int MAX_PACKET_SIZE = 2048;

    private DatagramSocket socket;
    private ReceiverListener listener;

    public UdpReceiver(DatagramSocket socket, ReceiverListener listener) {
        this.socket = socket;
        this.listener = listener;
    }

    public void run() {
        System.out.println("UdpReceiver listening on port " + socket.getLocalPort());

        while (!socket.isClosed()) {
            byte[] buffer = new byte[MAX_PACKET_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (Exception e){
                e.printStackTrace();
                break;
            }
            String msg = new String(packet.getData(), 0, packet.getLength());
            
            System.out.println("--------------------------------");
            System.out.println("UdpReceiver Received: " + msg);
            System.out.println("--------------------------------\n");

            listener.onReceive(packet);
        }
    }
}

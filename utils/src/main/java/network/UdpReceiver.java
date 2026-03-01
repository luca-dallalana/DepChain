package network;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UdpReceiver implements Runnable{

    private static final int MAX_PACKET_SIZE = 2048;

    private final int port;
    private ReceiverListener listener;


    public UdpReceiver(int port, ReceiverListener listener) {
        this.port = port;
        this.listener = listener;
    }

    public void run() { //FIXME maybe send socket as parameter and close it in member
        try (DatagramSocket socket = new DatagramSocket(port)) {

            System.out.println("UdpReceiver listening on port " + port);

            while (!socket.isClosed()) {
                byte[] buffer = new byte[MAX_PACKET_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                socket.receive(packet);

                String msg = new String(packet.getData(), 0, packet.getLength());
                System.out.println("UdpReceiver Received: " + msg);

                listener.onReceive(packet);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
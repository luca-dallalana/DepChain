import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkLayerLib {

    ConcurrentHashMap<Integer, Boolean> receivedAck = new ConcurrentHashMap<>();

    // Authenticated perfect links using SLs
    public void alpSend(DatagramSocket socket,String m, String dest, Integer port, Integer seq) throws IOException {
        byte[] data = m.getBytes();
        InetAddress address = InetAddress.getByName(dest);
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        // ADD AUTHENTICATION HERE
        slSend(socket, packet, seq);
        //socket.close();
    }
    // Stubborn Links
    public void slSend(DatagramSocket socket, DatagramPacket packet, Integer seq) throws IOException {
   
        while (true) {
            socket.send(packet);
            System.out.println("Sent: " + new String(packet.getData(), 0, packet.getLength()));
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if(receivedAck.getOrDefault(seq, false)) {
                break;
            }
        }   
    }  
    
    public void alpDeliver(DatagramSocket socket){
        byte[] buffer = new byte[1024];
        
        while (!socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); 
                String msg = new String(packet.getData(), 0, packet.getLength());
                // ADD AUTHENTICATION CHECK HERE
                System.out.println("Received this: " + msg);
                // Send ACK back to sender
                if (msg.startsWith("SEQ=")) {
                    int seq = Integer.parseInt(msg.substring(4).split(" ")[0]);
                    String sendMsg = "ACK=" + seq;
                    DatagramPacket ackPacket = new DatagramPacket(sendMsg.getBytes(), sendMsg.getBytes().length, packet.getAddress(), packet.getPort());
                    // MAYBE ADD DUP ACK CHECK HERE
                    socket.send(ackPacket);
                }
                System.out.println("Received: " + msg);
            } catch (Exception e) {
                System.out.println("error" + e.getMessage());
            }
        }
    }

    public void filterReceive(DatagramSocket socket) throws IOException {
        byte[] buffer = new byte[1024];
        while (!socket.isClosed()) {
            
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                socket.receive(packet); 

                String msg = new String(packet.getData(), 0, packet.getLength());

                if (msg.startsWith("ACK=")) {
                    int seq = Integer.parseInt(msg.substring(4));
                    receivedAck.put(seq, true);
                    System.out.println("Received ACK for SEQ=" + seq);
                }

            } catch (Exception e) {
                break;
            }
        }
    }
}

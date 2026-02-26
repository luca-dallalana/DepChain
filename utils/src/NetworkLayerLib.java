import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
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
                Thread.sleep(6000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if(receivedAck.getOrDefault(seq, false)) {
                break;
            }
        }   
    }  
    
    public void alpDeliver(DatagramSocket socket){
        byte[] buffer = new byte[2048];
        
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
                    if (msg.contains("DH")) {
                        System.out.println("Received DH message: " + msg);
                        String pubKeyB64 = msg.split(" ")[2];
                        byte[] pubKeyBytes = Base64.getDecoder().decode(pubKeyB64);
                        KeyFactory kf = KeyFactory.getInstance("DiffieHellman");
                        PublicKey pubkey = kf.generatePublic(new java.security.spec.X509EncodedKeySpec(pubKeyBytes));
                        KeyPair keys = AuthLib.generateDHKeyPairReceiver(pubkey);
                        String myPubKeyB64 = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
                        sendMsg += " DH " + myPubKeyB64;
                        byte[] sharedSecret = AuthLib.computeSharedSecret(keys.getPrivate(), pubkey);
                        System.out.println("---> Shared secret computed: " + Base64.getEncoder().encodeToString(sharedSecret));
                        DatagramPacket ackPacket = new DatagramPacket(sendMsg.getBytes(), sendMsg.getBytes().length, packet.getAddress(), packet.getPort());
                        socket.send(ackPacket);
                        return;
                    }
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

    public void filterReceive(DatagramSocket socket, PrivateKey privKey) throws IOException {
        byte[] buffer = new byte[2048];
        while (!socket.isClosed()) {
            
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                socket.receive(packet); 

                String msg = new String(packet.getData(), 0, packet.getLength());

                if (msg.startsWith("ACK=")) {
                    System.out.println("Received: " + msg);
                    if (msg.contains("DH")) {
                        System.out.println("Received DH ACK: " + msg);
                        KeyFactory kf = KeyFactory.getInstance("DiffieHellman");
                        String pubKeyB64 = msg.split(" ")[2];
                        byte[] pubKeyBytes = Base64.getDecoder().decode(pubKeyB64);
                        PublicKey pubkey = kf.generatePublic(new java.security.spec.X509EncodedKeySpec(pubKeyBytes));
                        System.out.println("WILL COMPUTE SHARED SECRET WITH: " + pubKeyB64);
                        byte[] sharedSecret = AuthLib.computeSharedSecret(privKey, pubkey);
                        System.out.println("---> Shared secret computed: " + Base64.getEncoder().encodeToString(sharedSecret));
                    }
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

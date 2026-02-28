import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKey;


public class NetworkLayerLib implements ReceiverListener {

    private DeliveryListener listener;
    private DatagramSocket socket;

    private ConcurrentHashMap<Integer, Boolean> receivedAck = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, String> outOfOrderMessages = new ConcurrentHashMap<>(); 
    private AtomicInteger nextExpectedSeq = new AtomicInteger(1); // Prevents concurrent access 
    private ConcurrentHashMap<Integer, SecretKey> sharedSecrets = new ConcurrentHashMap<>();


    
    public NetworkLayerLib(DeliveryListener listener, int port){
        this.listener = listener;
        try {
            this.socket = new DatagramSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private String verifyAndRemoveHmac(String message) {
        int idx = message.indexOf(" HMAC=");
        if (idx == -1) {
            return message;
        }
        String payload = message.substring(0, idx);
        String hmacStr = message.substring(idx + 6);
        SecretKey key = sharedSecrets.get(1);

        try {
            boolean ok = AuthLib.verifyHmac(payload.getBytes(), Base64.getDecoder().decode(hmacStr), key);
            if (!ok) {
                System.out.println("HMAC check failed for message: " + message);
                return null;
            }
        } catch (Exception e) {
            System.out.println("Error verifying HMAC: " + e.getMessage());
            return null;
        }
        return payload;
    }

    // Authenticated perfect links using SLs
    public void alpSend(String m, String dest, Integer port, Integer seq) throws IOException {
        byte[] data = m.getBytes();
        InetAddress address = InetAddress.getByName(dest);
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        // ADD AUTHENTICATION HERE
        
        new Thread(() -> {
            try {
                slSend(packet, seq);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
        
    }

    // Stubborn Links
    public void slSend(DatagramPacket packet, Integer seq) throws IOException {
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


    @Override
    public void onReceive(DatagramPacket packet) {
        System.out.println("NetworkLayerLib received from UdpReceiver: " + new String(packet.getData(), 0, packet.getLength()));
        try {
            alpDeliver(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Diogo check here if its DH then we can only use this and create a auth protocol in memeber once they are launched

    public void alpDeliver(DatagramPacket packet) throws IOException {
        String message = new String(packet.getData(), 0, packet.getLength());
        System.out.println("Delivering message: " + message);

        String prefix = message.split("=")[0];

        switch (prefix) {
            case "DH REQ":
                System.out.println("Received DH request message: " + message);
                try {
                    sendDHResponse(packet);
                } catch (Exception e) {
                    System.out.println("Error generating shared secret: " + e.getMessage());
                }
                break;
            case "DH RESP":
                System.out.println("Received DH response message: " + message);
                receivedAck.put(1, true); //FIXME is it a problem to be hardcoded ?
                listener.onDeliver(0, message);
                break;
            case "ACK":
                System.out.println("Received ACK: " + message);
                String strippedAck = verifyAndRemoveHmac(message);
                if (strippedAck == null) break;
                int seqAck = Integer.parseInt(strippedAck.substring(4));
                if (!receivedAck.containsKey(seqAck)) { // Dup ACK check
                    receivedAck.put(seqAck, true);
                }
                break;
            case "SEQ":
                System.out.println("Received SEQ: " + message);
                String strippedSeq = verifyAndRemoveHmac(message);
                if (strippedSeq == null) break;
                int seq = Integer.parseInt(strippedSeq.substring(4).split(" ")[0]);
                sendAck(packet, seq);
                
                if (seq == nextExpectedSeq.get()) {
                    listener.onDeliver(seq, strippedSeq);
                    nextExpectedSeq.incrementAndGet();
                    // Check if we can delivered stored out of order messages
                    while (outOfOrderMessages.containsKey(nextExpectedSeq.get())) {
                        String storedMsg = outOfOrderMessages.remove(nextExpectedSeq.get());
                        listener.onDeliver(nextExpectedSeq.get(), storedMsg);
                        nextExpectedSeq.incrementAndGet();
                    }
                } else {
                    System.out.println("Message out of order. Expected seq: " + nextExpectedSeq + ", received seq: " + seq);
                    outOfOrderMessages.put(seq, strippedSeq);
                }
                break;
            default:
                //check auth
                break;
        }
    }

    public void sendAck(DatagramPacket packet, Integer seq) throws IOException {
        String ackMsg = "ACK=" + seq;
        byte[] data = ackMsg.getBytes();
        InetAddress address = packet.getAddress();
        int port = packet.getPort();
        DatagramPacket ackPacket = new DatagramPacket(data, data.length, address, 3002);//FIXME HARDCODED PORT for testing
        socket.send(ackPacket);
        System.out.println("Sent ACK: " + ackMsg);
    }

    public void sendDHResponse(DatagramPacket packet) throws Exception {
        String msg = new String(packet.getData(), 0, packet.getLength());
        String pubKeyB64 = msg.split(" ")[2];
        byte[] pubKeyBytes = Base64.getDecoder().decode(pubKeyB64);
        KeyFactory kf = KeyFactory.getInstance("DiffieHellman");
        PublicKey pubkey = kf.generatePublic(new java.security.spec.X509EncodedKeySpec(pubKeyBytes));
        KeyPair keys = AuthLib.generateDHKeyPairReceiver(pubkey);
        String myPubKeyB64 = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        String DHresponse= "DH RESP= " + myPubKeyB64;
        byte[] sharedSecret = AuthLib.computeSharedSecret(keys.getPrivate(), pubkey);
        SecretKey hmacKey = AuthLib.deriveHmacKey(sharedSecret);
        sharedSecrets.put(1, hmacKey);  // FIXME hardcoded senderId for testing
        System.out.println("---> Shared secret derived and stored for sender 1");
        DatagramPacket DHresponsePacket = new DatagramPacket(DHresponse.getBytes(), DHresponse.getBytes().length, packet.getAddress(), 3002);//FIXME HARDCODED PORT for testing
        socket.send(DHresponsePacket);
        listener.onDeliver(0, Base64.getEncoder().encodeToString(sharedSecret)); //FIXME MISSING senderId EXTRACTION and also this to string is wrong maybe
    }

    public void closeSocket() {
        socket.close();
    }

    public void addSharedSecret(Integer senderId, SecretKey secretKey) {
        sharedSecrets.put(senderId, secretKey);
    }

    public SecretKey getSharedSecret(Integer senderId) {
        return sharedSecrets.get(senderId);
    }
}

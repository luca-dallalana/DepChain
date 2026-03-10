package network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.SecretKey;

import crypto.CryptoLib;


public class NetworkLayerLib implements ReceiverListener {

    private ConcurrentHashMap<Integer, KeyPair> dhKeyPairs = new ConcurrentHashMap<>();
    private DeliveryListener listener;
    private DatagramSocket socket;
    private String PRIVATE_KEY_PATH;

    private ConcurrentHashMap<Integer, Integer> sentSeq = new ConcurrentHashMap<>(); // Maps ports -> sequence number
    private ConcurrentHashMap<Integer, Set<Integer>> unAcked= new ConcurrentHashMap<>(); // Maps ports -> set of missing ACKs
    private ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, String>> outOfOrderMessages = new ConcurrentHashMap<>(); // Maps ports -> (seq -> message)
    private ConcurrentHashMap<Integer, AtomicInteger> nextExpectedSeq = new ConcurrentHashMap<>(); // Maps ports -> expected seq
    private ConcurrentHashMap<Integer, SecretKey> sharedSecrets = new ConcurrentHashMap<>(); // Maps ports -> shared secrets

    public NetworkLayerLib(DeliveryListener listener, DatagramSocket socket){
        this.listener = listener;
        this.socket = socket;

        if (socket.getLocalPort() >= 4000) {
            int clientId = socket.getLocalPort() - 4000;
            PRIVATE_KEY_PATH = "../rsa_keys/client_" + clientId + "/client_" + clientId + ".privatekey";
        } else {
            int replicaId = socket.getLocalPort() - 3000;
            PRIVATE_KEY_PATH = "../rsa_keys/server_" + replicaId + "/server_" + replicaId + ".privatekey";
        }
    }

    // Authenticated perfect links using SLs
    public void alpSend(String m, String dest, Integer port) throws IOException {
        int seq = sentSeq.getOrDefault(port, 0) + 1;
        sentSeq.put(port, seq);

        if (!sharedSecrets.containsKey(port)) {
            new Thread(() -> {
                try {
                    handleDH(dest, port, seq);
                    sendAuthenticatedMessage(m, dest, port, seq);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
            return;
        }

        sendAuthenticatedMessage(m, dest, port, seq);
    }

    public void sendAuthenticatedMessage(String m, String dest, Integer port, int seq) throws IOException {
        String msg = "SEQ=" + seq + " " + m;
        SecretKey key = sharedSecrets.get(port);
        
        try {
            String hmac = CryptoLib.computeHmac(msg.getBytes(), key);
            msg += " HMAC=" + hmac;
        } catch (Exception e) {
            System.out.println("Error computing HMAC: " + e.getMessage());
            return;
        }

        byte[] data = msg.getBytes();
        InetAddress address = InetAddress.getByName(dest);
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        
        new Thread(() -> {
            try {
                slSend(packet, seq);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Stubborn Links
    public void slSend(DatagramPacket packet, int seq) throws IOException {
        int port = packet.getPort();
        unAcked.putIfAbsent(port, ConcurrentHashMap.newKeySet());
        unAcked.get(port).add(seq);
        while (unAcked.get(port).contains(seq)) {
            socket.send(packet);
            System.out.println("--------------------------------");
            System.out.println("Sent: " + new String(packet.getData(), 0, packet.getLength()) + " to port " + packet.getPort());
            System.out.println("--------------------------------\n");
            try {
                Thread.sleep(600);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }   
    }  

    @Override
    public void onReceive(DatagramPacket packet) {
        //System.out.println("--------------------------------");
        //System.out.println("NetworkLayerLib received from UdpReceiver: " + new String(packet.getData(), 0, packet.getLength()));
        //System.out.println("--------------------------------\n");
        try {
            alpDeliver(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void alpDeliver(DatagramPacket packet) throws IOException {
        String message = new String(packet.getData(), 0, packet.getLength());

        String prefix = message.split("=")[0];

        switch (prefix) {
            case "DH REQ":
                //System.out.println("Received DH request message from port " + packet.getPort());
                if (sharedSecrets.containsKey(packet.getPort())) {

                    System.out.println("Shared secret already exists for port " + packet.getPort());
                }
                try {
                    if (!dhKeyPairs.containsKey(packet.getPort()) || packet.getPort() > socket.getLocalPort()) {
                        sendDHResponse(packet);
                    } else {
                        sendDHExists(packet);
                    }
                } catch (Exception e) {
                    System.out.println("Error generating shared secret: " + e.getMessage());
                }
                break;
            case "DH RESP":
               // System.out.println("Received DH response message from port " + packet.getPort());
                
                int DHseq = Integer.parseInt(message.split(" ")[4]);
                int port = packet.getPort();

                if (sharedSecrets.containsKey(packet.getPort()) && message.contains("STOP")) {
                    System.out.println("Shared secret already exists for port " + packet.getPort());
   
                } else {
                    if (!verifyRsaSignature(packet, message)) {
                        System.out.println("Invalid RSA signature in DH response from port " + packet.getPort());
                        return;
                    }
                    generateSharedSecret(message, port);

                }

                if (unAcked.get(port) != null) {
                    unAcked.get(port).remove(DHseq); // Remove DH seq from unAcked if present
                }

                break;
            case "ACK":
                //System.out.println("Received ACK: " + message);
                String strippedAck = verifyAndRemoveHmac(message, packet.getPort());
                if (strippedAck == null) break;
                int seqAck = Integer.parseInt(strippedAck.substring(4));
                if (unAcked.get(packet.getPort()) != null) {
                    unAcked.get(packet.getPort()).remove(seqAck); // Remove ACK seq from unAcked if present
                }
                break;
            case "SEQ":
                //System.out.println("Received SEQ: " + message);
                String strippedSeq = verifyAndRemoveHmac(message, packet.getPort());
                if (strippedSeq == null) break;
                int seq = Integer.parseInt(strippedSeq.substring(4).split(" ")[0]);
                sendAck(packet, seq);
                
                if (nextExpectedSeq.get(packet.getPort()) == null) {
                    nextExpectedSeq.put(packet.getPort(), new AtomicInteger(1));
                }

                if (seq == nextExpectedSeq.get(packet.getPort()).get()) {
                    listener.onDeliver(packet.getPort(), strippedSeq);
                    nextExpectedSeq.get(packet.getPort()).incrementAndGet();
                    // Check if we can delivered stored out of order messages
                    while (outOfOrderMessages.get(packet.getPort()) != null && outOfOrderMessages.get(packet.getPort()).containsKey(nextExpectedSeq.get(packet.getPort()).get())) {
                        String storedMsg = outOfOrderMessages.get(packet.getPort()).remove(nextExpectedSeq.get(packet.getPort()).get());
                        listener.onDeliver(packet.getPort(), storedMsg);
                        nextExpectedSeq.get(packet.getPort()).incrementAndGet();
                    }
                } else {
                    if (seq < nextExpectedSeq.get(packet.getPort()).get()) { // Duplicate message, already delivered
                        System.out.println("Duplicate message received. Expected seq: " + nextExpectedSeq.get(packet.getPort()) + ", received seq: " + seq);
                    } else {
                        System.out.println("Message out of order. Expected seq: " + nextExpectedSeq.get(packet.getPort()) + ", received seq: " + seq);
                        if (outOfOrderMessages.get(packet.getPort()) == null) {
                            outOfOrderMessages.put(packet.getPort(), new ConcurrentHashMap<>());
                        }
                        outOfOrderMessages.get(packet.getPort()).put(seq, strippedSeq);
                    }
                }
                break;
            default:
                System.out.println("Unknown message type received: " + message);
                break;
        }
    }

    public void sendAck(DatagramPacket packet, Integer seq) throws IOException {
        String ackMsg = "ACK=" + seq;
        byte[] data = ackMsg.getBytes();
        InetAddress address = packet.getAddress();
        int port = packet.getPort();
        DatagramPacket ackPacket = new DatagramPacket(data, data.length, address, port);
        socket.send(ackPacket);
        System.out.println("Sent ACK: " + ackMsg);
    }

    /* ===== Diffie–Hellman ===== */

    public void sendDHResponse(DatagramPacket packet) throws Exception {
        String msg = new String(packet.getData(), 0, packet.getLength());
        String pubKeyB64 = msg.split(" ")[2];
        String seq = msg.split(" ")[4];

        if (!verifyRsaSignature(packet, msg)) {
            System.out.println("Invalid RSA signature in DH request from port " + packet.getPort());
            return;
        }
  
        byte[] pubKeyBytes = Base64.getDecoder().decode(pubKeyB64);
        KeyFactory kf = KeyFactory.getInstance("DiffieHellman");
        PublicKey pubkey = kf.generatePublic(new java.security.spec.X509EncodedKeySpec(pubKeyBytes));
        if(!dhKeyPairs.containsKey(packet.getPort())) {
            KeyPair keys = CryptoLib.generateDHKeyPairReceiver(pubkey);
            dhKeyPairs.put(packet.getPort(), keys);
        }
        byte[] sharedSecret = CryptoLib.computeSharedSecret(dhKeyPairs.get(packet.getPort()).getPrivate(), pubkey);
        SecretKey hmacKey = CryptoLib.deriveHmacKey(sharedSecret);
        sharedSecrets.put(packet.getPort(), hmacKey);
        //System.out.println("---> Shared secret derived and stored for sender " + packet.getPort());
        
        System.out.println("Sending DH response to " + packet.getPort());
        String myPubKeyB64 = Base64.getEncoder().encodeToString(dhKeyPairs.get(packet.getPort()).getPublic().getEncoded());
        String DHresponse= "DH RESP= " + myPubKeyB64 + " SEQ= " + seq + " ";

        try {
            byte[] signature = CryptoLib.sign(DHresponse.getBytes(), PRIVATE_KEY_PATH);
            String signatureB64 = Base64.getEncoder().encodeToString(signature);
            DHresponse += "RSA_SIG= " + signatureB64;   
        } catch (Exception e) {
            System.out.println("Error signing DH response: " + e.getMessage());
            return;
        }

        DatagramPacket DHresponsePacket = new DatagramPacket(DHresponse.getBytes(), DHresponse.getBytes().length, packet.getAddress(), packet.getPort());
        socket.send(DHresponsePacket);
    }

    public void sendDHExists(DatagramPacket packet) throws Exception {
        String msg = new String(packet.getData(), 0, packet.getLength());
        String seq = msg.split(" ")[4];

        if (!verifyRsaSignature(packet, msg)) {
            System.out.println("Invalid RSA signature in DH request from port " + packet.getPort());
            return;
        }   
        
        System.out.println("Sending DH Already computed response to " + packet.getPort());
        
        String DHresponse= "DH RESP= " + "STOP" + " SEQ= " + seq + " ";

        try {
            byte[] signature = CryptoLib.sign(DHresponse.getBytes(), PRIVATE_KEY_PATH);
            String signatureB64 = Base64.getEncoder().encodeToString(signature);
            DHresponse += "RSA_SIG= " + signatureB64;   
        } catch (Exception e) {
            System.out.println("Error signing DH response: " + e.getMessage());
            return;
        }

        DatagramPacket DHresponsePacket = new DatagramPacket(DHresponse.getBytes(), DHresponse.getBytes().length, packet.getAddress(), packet.getPort());
        socket.send(DHresponsePacket);
    }


    public void handleDH(String dest, int port, int seq) {

        try {
            if (!dhKeyPairs.containsKey(port)) {
                dhKeyPairs.put(port, CryptoLib.generateDHKeyPair());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        byte[] pubKeyBytes = dhKeyPairs.get(port).getPublic().getEncoded();
        String pubKeyB64 = Base64.getEncoder().encodeToString(pubKeyBytes);
        String DHrequest = "DH REQ= " + pubKeyB64 + " SEQ= " + seq + " ";
        
        try {
            byte[] signature = CryptoLib.sign(DHrequest.getBytes(), PRIVATE_KEY_PATH);
            String signatureB64 = Base64.getEncoder().encodeToString(signature);
            DHrequest += "RSA_SIG= " + signatureB64;   
        } catch (Exception e) {
            System.out.println("Error signing DH request: " + e.getMessage());
            return;
        }

        try {
            byte[] data = DHrequest.getBytes();
            InetAddress address = InetAddress.getByName(dest);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            
            slSend(packet, seq);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void generateSharedSecret(String msg, int port) {
        try {
            KeyFactory kf = KeyFactory.getInstance("DiffieHellman");
            String pubKeyB64 = msg.split(" ")[2];
            byte[] pubKeyBytes = Base64.getDecoder().decode(pubKeyB64);
            PublicKey pubkey = kf.generatePublic(new java.security.spec.X509EncodedKeySpec(pubKeyBytes));
            //System.out.println("WILL COMPUTE SHARED SECRET WITH: " + pubKeyB64);
            byte[] sharedSecret = CryptoLib.computeSharedSecret(dhKeyPairs.get(port).getPrivate(), pubkey);
            SecretKey hmacKey = CryptoLib.deriveHmacKey(sharedSecret);
            sharedSecrets.put(port, hmacKey);
            //System.out.println("---> Shared secret derived and stored for sender " + port);
        } catch (Exception e) {
            System.out.println("Error generating shared secret: " + e.getMessage());
        }
    }

    /* ===== HMAC ===== */

    private String verifyAndRemoveHmac(String message, int port) {
        int idx = message.indexOf(" HMAC=");
        if (idx == -1) {
            return message;
        }
        String payload = message.substring(0, idx);
        String hmacStr = message.substring(idx + 6);
        SecretKey key = sharedSecrets.get(port);

        try {
            boolean ok = CryptoLib.verifyHmac(payload.getBytes(), Base64.getDecoder().decode(hmacStr), key);
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

    /* ===== RSA_SIGNATURES ===== */

    private boolean verifyRsaSignature(DatagramPacket packet, String message) {
        String[] parts = message.split("RSA_SIG= ");
       
        if (parts.length < 2) {
            System.out.println("No RSA signature found in message: " + message);
            return false;
        }

        try {
            byte[] signature = Base64.getDecoder().decode(parts[1]);
            
            String public_key_path;

            if ( packet.getPort() >= 4000 ){
                int clientId = packet.getPort() - 4000;
                public_key_path = "../rsa_keys/client_" + clientId + "/client_" + clientId + ".pubkey";

            } else {
                int replicaId = packet.getPort() - 3000;
                public_key_path = "../rsa_keys/server_" + replicaId + "/server_" + replicaId + ".pubkey";
            }

            if (!CryptoLib.verifySignature(parts[0].getBytes(), signature, public_key_path)) {
                return false;
            }
        } catch (Exception e) {
            System.out.println("Error verifying RSA signature: " + e.getMessage());
            return false;
        }
        return true;
    }

}

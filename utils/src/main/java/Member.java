import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Base64;
import javax.crypto.SecretKey;

import network.DeliveryListener;
import network.NetworkLayerLib;
import network.UdpReceiver;
import crypto.CryptoLib;


public class Member implements DeliveryListener {
    private NetworkLayerLib networkLayerLib;
    private UdpReceiver receiver;
    private KeyPair dhKeyPair;

    public Member(int localPort, int listenerPort) {
        this.networkLayerLib = new NetworkLayerLib(this, localPort);
        this.receiver = new UdpReceiver(listenerPort, networkLayerLib);
        new Thread(receiver).start();
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("  send <localPort> <destIP> <destPort> <message> <listenPort>");
            System.out.println("  receive <listenPort>");
            System.out.println();
            return;
        }

        String mode = args[0];
        Member member = new Member(Integer.parseInt(args[1]), Integer.parseInt(args[5]));

        Integer localPort = Integer.parseInt(args[1]);
        String destIP = args[2];
        Integer destPort = Integer.parseInt(args[3]);
        String message = args[4];

        

        if (mode.equals("receive")) {
            member.start(localPort, destIP, destPort, message, true);
        } else if (mode.equals("send")) {
            if (args.length < 5) {
                System.out.println("Usage: java Member send <localPort> <destIP> <destPort> <message>");
                return;
            }

            member.startDH(destIP, destPort, 1); // Generate and send DH key first

            member.start(localPort, destIP, destPort, message, false);
        } else {
            System.out.println("Invalid mode. Use 'send' or 'receive'");
        }
    }


    private boolean startDH(String destIP, Integer destPort, Integer seq) throws IOException {
        try {
            dhKeyPair = CryptoLib.generateDHKeyPair();
        } catch (Exception e) {
            System.out.println("Error generating DH key pair: " + e.getMessage());
            return false;
        }
        byte[] pubKeyBytes = dhKeyPair.getPublic().getEncoded();
        String pubKeyB64 = Base64.getEncoder().encodeToString(pubKeyBytes);
        String DHrequest = "DH REQ= " + pubKeyB64;

        networkLayerLib.alpSend(DHrequest, destIP, destPort, seq);

        return true;
    }

    private void generateSharedSecret(String msg) throws Exception {
        KeyFactory kf = KeyFactory.getInstance("DiffieHellman");
        String pubKeyB64 = msg.split(" ")[2];
        byte[] pubKeyBytes = Base64.getDecoder().decode(pubKeyB64);
        PublicKey pubkey = kf.generatePublic(new java.security.spec.X509EncodedKeySpec(pubKeyBytes));
        System.out.println("WILL COMPUTE SHARED SECRET WITH: " + pubKeyB64);
        byte[] sharedSecret = CryptoLib.computeSharedSecret(dhKeyPair.getPrivate(), pubkey);
        SecretKey hmacKey = CryptoLib.deriveHmacKey(sharedSecret);
        networkLayerLib.addSharedSecret(1, hmacKey);
        System.out.println("---> Shared secret derived and stored for sender " + 1);
    }

    @Override
    public void onDeliver(int senderId, String message) {
        //FIXME missing logic
        System.out.println("Member received message from sender " + senderId + ": " + message);
        if (message.startsWith("DH RESP=")) {
            System.out.println("Member Received DH response message: " + message);
            try {
                generateSharedSecret(message);
            } catch (Exception e) {
                System.out.println("Error generating shared secret: " + e.getMessage());
            }
        }
    }

    private void start(Integer localPort, String destIP, Integer destPort, String message, boolean receiver) throws IOException {
        // Add sequence number (you can modify this to be dynamic)
        if (receiver) {
            System.out.println("=== RECEIVER MODE ===");
            System.out.println("Listening on port: " + localPort);
            while (true) {
                System.out.println("Waiting for messages...\n");
                try {
                    Thread.sleep(9000); // Sleep to reduce busy waiting
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        
        int seq = 1;
        String formattedMessage = "SEQ=" + seq + " " + message;
        
        // FIXME: change this wait logic 
        SecretKey key = null;
        int maxWait = 50; 
        for (int i = 0; i < maxWait; i++) {
            key = networkLayerLib.getSharedSecret(1);
            if (key != null) break;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        String hmac = "";
        if (key != null) {
            try {
                hmac = CryptoLib.computeHmac(formattedMessage.getBytes(), key);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Warning: shared secret not available, sending without HMAC");
        }
        String finalMessage = formattedMessage + " HMAC=" + hmac;
        System.out.println("=== SENDER MODE ===");
        System.out.println("Local port: " + localPort);
        System.out.println("Destination: " + destIP + ":" + destPort);
        System.out.println("Message: " + formattedMessage);
        System.out.println("Sending...");

        networkLayerLib.alpSend(finalMessage, destIP, destPort, seq);

        System.out.println("### Sender finished. ###");
    }

}
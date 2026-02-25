import java.io.IOException;
import java.net.DatagramSocket;

public class Member {
    private static NetworkLayerLib networkLayerLib = new NetworkLayerLib();
    
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: java Member <mode> <port>");
            System.out.println("Modes:");
            System.out.println("  send <localPort> <destIP> <destPort> <message>");
            System.out.println("  receive <listenPort>");
            System.out.println();
            return;
        }
        
        String mode = args[0];
        
        if (mode.equals("receive")) {
            // Receiver mode
            Integer listenPort = Integer.parseInt(args[1]);
            startReceiver(listenPort);
            
        } else if (mode.equals("send")) {
            // Sender mode
            if (args.length < 5) {
                System.out.println("Usage: java Member send <localPort> <destIP> <destPort> <message>");
                return;
            }
            
            Integer localPort = Integer.parseInt(args[1]);
            String destIP = args[2];
            Integer destPort = Integer.parseInt(args[3]);
            String message = args[4];
            
            startSender(localPort, destIP, destPort, message);
            
        } else {
            System.out.println("Invalid mode. Use 'send' or 'receive'");
        }
    }
    
    private static void startSender(Integer localPort, String destIP, Integer destPort, String message) throws IOException {
        // Add sequence number (you can modify this to be dynamic)
        int seq = 1;
        String formattedMessage = "SEQ=" + seq + " " + message;
        
        System.out.println("=== SENDER MODE ===");
        System.out.println("Local port: " + localPort);
        System.out.println("Destination: " + destIP + ":" + destPort);
        System.out.println("Message: " + formattedMessage);
        System.out.println("Sending...");
        
        DatagramSocket sendSocket = new DatagramSocket(localPort);
        
        // Start a thread to listen for ACKs
        Thread ackListener = new Thread(() -> {
            try {
                networkLayerLib.filterReceive(sendSocket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        ackListener.start();
        
        // Send the message
        networkLayerLib.alpSend(sendSocket, formattedMessage, destIP, destPort, seq);

        
        sendSocket.close();
        System.out.println("Sender finished.");
    }
    
    private static void startReceiver(Integer listenPort) throws IOException {
        System.out.println("=== RECEIVER MODE ===");
        System.out.println("Listening on port: " + listenPort);
        System.out.println("Waiting for messages...\n");
        
        DatagramSocket deliverSocket = new DatagramSocket(listenPort);
        
        // Start delivering messages
        networkLayerLib.alpDeliver(deliverSocket);
    }
}
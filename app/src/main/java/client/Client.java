package client;
import java.net.DatagramSocket;
import java.util.Base64;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import config.ClientConfig;
import crypto.CryptoLib;
import info.ReplicaInfo;
import network.DeliveryListener;
import network.NetworkLayerLib;
import network.UdpReceiver;

public class Client implements DeliveryListener{
    private ClientConfig config;
    private DatagramSocket socket;
    private boolean running = true;
    private NetworkLayerLib networkLayerLib;
    private UdpReceiver receiver;
    private ConcurrentHashMap<Integer, String> receivedDecided = new ConcurrentHashMap<>(); //maps port -> decided command
    private boolean decided = false;
    private int sequenceNumber = 0;

    public Client(ClientConfig config, DatagramSocket socket) {
        this.config = config;
        this.socket = socket;
        this.networkLayerLib = new NetworkLayerLib(this, socket);

    }
    
    public void start() {
        System.out.println("===== Client STARTED =====");
        this.receiver = new UdpReceiver(socket, networkLayerLib);
        new Thread(receiver).start();
        startCLI();
    }
    public void startCLI() {
        
        System.out.println("\n=========== Client ==========");
        System.out.println("Commands:");
        System.out.println("  send <message> - Send a message to the server");
        System.out.println("  exit           - Exit the client");
        System.out.println("===================================\n");
        
        Scanner scanner = new Scanner(System.in);
        while (this.running) {
       
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            
            if (input.isEmpty()) {
                continue;
            }
            
            if (input.equalsIgnoreCase("exit")) {
                break;
            } else if (input.startsWith("send ")) {
                sequenceNumber++;
                String message = sequenceNumber + " " + input.substring(5);
                try {
                    sendMessage(message);
                    while (!decided) {
                        Thread.sleep(100); // Wait 2F+1 responses
                    }
                    String mostCommon = getMostCommonDecided();
                    System.out.println("The command: " + mostCommon + " was decided.");
                    decided = false; // reset for next command
                    receivedDecided.clear();
                } catch (Exception e) {
                    System.err.println("Error sending message: " + e.getMessage());
                }
            } else {
                System.out.println("Unknown command. Try 'send <message>' or 'exit'");
            }
           
        }
        scanner.close();
    }
    private void sendMessage(String m) throws java.io.IOException {
        String packet = "NewCommand=" + m;
        byte[] signature;
        String PRIVATE_KEY_PATH = "../rsa_keys/client_" + config.getID() + "/client_" + config.getID() + ".privatekey";
        try {
            signature = CryptoLib.sign(m.getBytes(), PRIVATE_KEY_PATH);
            String signatureB64 = Base64.getEncoder().encodeToString(signature);
            packet += "SIG=" + signatureB64;
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (ReplicaInfo replica : config.getAllReplicas()) {
            networkLayerLib.alpSend(packet, replica.getIP(), replica.getPort());
        }
    }

    @Override
    public void onDeliver(int senderPort, String message) {
        
        String payload = message;
        
        if (payload.startsWith("SEQ=")) {
            int idx = payload.indexOf(' ');
            if (idx != -1) {
                payload = payload.substring(idx + 1);
            }
        }
        if (payload.startsWith("DECIDED=")) {
            String reply = payload.substring("DECIDED=".length());
            System.out.println("Received reply: " + reply);
            addDecidedCommand(senderPort, reply);
        }
    }

    private void addDecidedCommand(int port, String command) {
        if (receivedDecided.containsKey(port)) return;
        receivedDecided.put(port, command);
        long count = receivedDecided.values().stream()
            .filter(c -> c.equals(command)).count();
        if (count >= config.getF() + 1) decided = true;
        
    }

    private String getMostCommonDecided() {
        return receivedDecided.values().stream()
            .collect(java.util.stream.Collectors.groupingBy(cmd -> cmd, java.util.stream.Collectors.counting()))
            .entrySet().stream()
            .max(java.util.Map.Entry.comparingByValue())
            .map(java.util.Map.Entry::getKey)
            .orElse(null);
    }

}

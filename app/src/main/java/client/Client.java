package client;
import java.net.DatagramSocket;
import java.util.Scanner;

import config.ClientConfig;
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
                String message = input.substring(5);
                try {
                    sendMessage(message);
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
        for (ReplicaInfo replica : config.getAllReplicas()) {
            networkLayerLib.alpSend(packet, replica.getIP(), replica.getPort());
        }
    }
    @Override
    public void onDeliver(int senderPort, String message) {

    }

}

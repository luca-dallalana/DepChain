package client;
import java.net.DatagramSocket;
import java.util.Scanner;

import network.DeliveryListener;
import network.NetworkLayerLib;
import network.UdpReceiver;

public class Client implements DeliveryListener{
    
    private String serverIp;
    private int serverPort;
    private int localPort;
    private DatagramSocket socket;
    private boolean running = true;
    private NetworkLayerLib networkLayerLib;
    private UdpReceiver receiver;


    public Client(String serverIp, int serverPort, int localPort, DatagramSocket socket) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.localPort = localPort;
        this.socket = socket;
        this.networkLayerLib = new NetworkLayerLib(this, this.socket);

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
                    sendMessage(message, "localhost", this.serverPort);
                } catch (Exception e) {
                    System.err.println("Error sending message: " + e.getMessage());
                }
            } else {
                System.out.println("Unknown command. Try 'send <message>' or 'exit'");
            }
           
        }
        scanner.close();
    }
    private void sendMessage(String m, String destIp, int destPort) throws java.io.IOException {
        String packet = "NewCommand=" + m;
        networkLayerLib.alpSend(packet, destIp, destPort);
    }
    @Override
    public void onDeliver(int senderId, String message) {

    }

}

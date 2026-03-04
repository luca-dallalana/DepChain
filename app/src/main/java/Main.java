import java.net.DatagramSocket;

import client.Client;

public class Main {
    
    public static void main(String[] args) {
        try {
            String serverIp = "localhost";
            int serverPort = 5000;
            int localPort = 6000;

            DatagramSocket socket = new DatagramSocket(4004);
            Client client = new Client(serverIp, serverPort, localPort, socket);
            client.start();
        } catch (Exception e) {
            System.err.println("Failed to start client: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

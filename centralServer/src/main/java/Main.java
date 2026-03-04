import java.net.DatagramSocket;

import central.CentralApp;

public class Main {
    public static void main(String[] args) {
        try {
            String serverIp = "localhost";
            int localPort = 5000;

            DatagramSocket socket = new DatagramSocket(localPort);
            CentralApp centralServer = new CentralApp(localPort, 4, socket); // FIXME: hardcoded numMembers
            centralServer.start();
        } catch (Exception e) {
            System.err.println("Failed to start central server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

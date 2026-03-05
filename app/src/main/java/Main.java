import java.net.DatagramSocket;

import client.Client;
import config.ClientConfig;

public class Main {
    
    public static void main(String[] args) {
        try {
            if (args.length < 2) {
                System.err.println("Usage: java Main <clientId> <numReplicas>");
                System.exit(1);
            }

            int thisID = Integer.parseInt(args[0]);
            int N = Integer.parseInt(args[1]);
            int f = (N - 1) / 3;

            ClientConfig config = new ClientConfig(N, thisID, null);

            int port = 4000 + thisID;
            DatagramSocket socket = new DatagramSocket(port);
            Client client = new Client(config, socket);
            client.start();

        } catch (Exception e) {
            System.err.println("Failed to start replica: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

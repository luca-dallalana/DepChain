import client.Client;
public class Main {
    
    public static void main(String[] args) {
        String serverIp = "localhost";
        int serverPort = 5000;
        int localPort = 6000;

        Client client = new Client(serverIp, serverPort, localPort);
        client.startCLI();
    }
}

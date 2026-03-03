import central.CentralApp;
public class Main {
    public static void main(String[] args) {
        String serverIp = "localhost";
        int localPort = 5000;

        CentralApp centralServer = new CentralApp(localPort, 4); // FIXME: hardcoded numMembers
        centralServer.start();
    }
}

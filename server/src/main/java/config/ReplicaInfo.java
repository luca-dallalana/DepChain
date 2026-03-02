package config;

import java.security.PublicKey;

public class ReplicaInfo {
    private final int ID;        // 0 to n-1
    private final String IP;     // IP address for network communication
    private final int port;             // Listening port
    private final PublicKey publicKey;  // For threshold signature verification (nullable for now)

    public ReplicaInfo(int ID, String IP, int port, PublicKey publicKey) {
        this.ID = ID;
        this.IP = IP;
        this.port = port;
        this.publicKey = publicKey;
    }

    public int getID() { return ID; }
    public String getIP() { return IP; }
    public int getPort() { return port; }
    public PublicKey getPublicKey() { return publicKey; }
}

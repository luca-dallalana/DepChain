package config;

import java.security.PublicKey;

public class ReplicaInfo {
    private final int replicaId;        // 0 to n-1
    private final String ipAddress;     // IP address for network communication
    private final int port;             // Listening port
    private final PublicKey publicKey;  // For threshold signature verification (nullable for now)

    public ReplicaInfo(int replicaId, String ipAddress, int port, PublicKey publicKey) {
        this.replicaId = replicaId;
        this.ipAddress = ipAddress;
        this.port = port;
        this.publicKey = publicKey;
    }

    public int getReplicaId() { return replicaId; }
    public String getIpAddress() { return ipAddress; }
    public int getPort() { return port; }
    public PublicKey getPublicKey() { return publicKey; }
}

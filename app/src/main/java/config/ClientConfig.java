package config;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import blockchain.AddressUtils;
import blockchain.Block;
import org.hyperledger.besu.datatypes.Address;

import info.ReplicaInfo;

public class ClientConfig {
    private final int ID;               // This replica's ID
    private final int N;                                    // Total number of replicas
    private final int F;                                    // Byzantine fault tolerance
    private ConcurrentHashMap<Integer, ReplicaInfo> replicas = new ConcurrentHashMap<>(); 
    private ConcurrentHashMap<Integer, Address> accountAddresses = new ConcurrentHashMap<>();
    private final Address istCoinContractAddress;

    public ClientConfig(int N, int thisID, PublicKey publicKey) {
        this.N = N;
        this.F = (N - 1) / 3; 
        this.ID = thisID;

        // Validate Byzantine fault tolerance: n = 3f + 1
        if (N < 3 * F + 1) {
            throw new IllegalArgumentException(
                "Invalid replica count. Must be n >= 3f + 1 for Byzantine fault tolerance. " +
                "Got n=" + N + " which gives f=" + F + " but requires n=" + (3*F+1)
            );
        }

        fillReplicasInfo();
        fillClientAccountAddresses();
        this.istCoinContractAddress = Address.fromHexString(Block.IST_COIN_ADDRESS); //FIXME this is a Diogo hack

      
    }

    public Address getAccountAddress(int replicaId) {
        return accountAddresses.get(replicaId);
    }

    public Address getISTCoinContractAddress() {
        return istCoinContractAddress;
    }

    // Leader election: round-robin based on view number
    public int getLeader(int viewNumber) {
        return viewNumber % N;
    }

    // Check if a replica ID is the current leader
    public boolean isLeader(int viewNumber) {
        return getLeader(viewNumber) == ID;
    }

    // Get replica information by ID
    public ReplicaInfo getReplicaInfo(int replicaId) {
        return replicas.get(replicaId);
    }

    // Get all replicas
    public Collection<ReplicaInfo> getAllReplicas() {
        return replicas.values();
    }

    // Getters
    public int getN() {
        return N; 
    }

    public int getF() {
        return F; 
    }
   
    public int getQuorumSize() {  // 2f+1 for quorum
         return 2 * F + 1; 
    }

    public int getID() { // return this replica's ID
        return ID; 
    }

    public int getPort() {
        return 4000 + ID; // Port is derived from ID
    }

    // Static factory for creating a default 4-replica configuration (f=1)
    private void fillReplicasInfo() {
        for (int i = 0; i < N ; i++) {
            replicas.put(i, new ReplicaInfo(i, "localhost", 3000 + i, null));
        }
    }

    private void fillClientAccountAddresses() {
        for (int i = 0; i < N; i++) {
            String pubKeyPath = "../rsa_keys/client_" + i + "/client_" + i + ".pubkey";
            if (!Files.exists(Paths.get(pubKeyPath))) {
                continue;
            }
            String addressHex = AddressUtils.generateAddressFromPublicKey(pubKeyPath);
            accountAddresses.put(i, Address.fromHexString(addressHex));
        }
    }

   
}

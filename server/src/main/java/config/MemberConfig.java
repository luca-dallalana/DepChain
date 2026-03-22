package config;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import blockchain.Transaction;
import info.ReplicaInfo;
import model.ClientRequest;

public class MemberConfig {
    private final int ID;               // This replica's ID
    private final int N;                                    // Total number of replicas
    private final int F;                                    // Byzantine fault tolerance
    private ConcurrentHashMap<Integer, ReplicaInfo> replicas = new ConcurrentHashMap<>();
    private Set<ClientRequest> pendingCommands = ConcurrentHashMap.newKeySet(); // Commands to be executed
    private ConcurrentHashMap<Integer, Integer> ClientsLastSequence = new ConcurrentHashMap<>(); // Track last request sequence number for each client maps client port -> lastSequenceNumber
    private List<Transaction> pendingTransactions = new ArrayList<>(); // Transactions to be included in blocks
    private byte[] blsPrivateKey;
    private List<byte[]> allPublicKeys;

    public MemberConfig(int N, int thisID, PublicKey publicKey) {
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

    // Static factory for creating a default 4-replica configuration (f=1)
    private void fillReplicasInfo() {
        for (int i = 0; i < N ; i++) {
            if (i != ID) {
                replicas.put(i, new ReplicaInfo(i, "localhost", 3000 + i, null));
            }
        }
    }


    public Set<ClientRequest> getPendingCommands() {
        return pendingCommands;
    }

    public void addPendingCommand(ClientRequest command) {
        this.pendingCommands.add(command);
    }

    public void removePendingCommand(ClientRequest command) {
        this.pendingCommands.remove(command);
    }

    public List<Transaction> getPendingTransactions() {
        return pendingTransactions;
    }

    public void addPendingTransaction(Transaction transaction) {
        this.pendingTransactions.add(transaction);
    }

    public void removePendingTransaction(Transaction transaction) {
        this.pendingTransactions.remove(transaction);
    }


    public void initializeBLSKeys(byte[] privateKey, List<byte[]> allPublicKeys) {
        this.blsPrivateKey = privateKey;
        this.allPublicKeys = allPublicKeys;
    }

    public byte[] getBlsPrivateKey() {
        return blsPrivateKey;
    }

    public List<byte[]> getAllPublicKeys() {
        return allPublicKeys;
    }

    public int getLastSequenceForClient(int clientPort) {
        return ClientsLastSequence.getOrDefault(clientPort, 0);
    }

    public void setLastSequenceForClient(int clientPort, int sequenceNumber) {
        ClientsLastSequence.put(clientPort, sequenceNumber);
    }

    public boolean isDuplicateRequest(Transaction request) {
        int lastSeq = getLastSequenceForClient(request.senderPort);
        return request.nonce_count <= lastSeq;
    }

}

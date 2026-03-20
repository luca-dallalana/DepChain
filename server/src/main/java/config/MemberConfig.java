package config;

import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import blockchain.Account;
import blockchain.Transaction;
import blockchain.WorldState;
import info.ReplicaInfo;
import model.ClientRequest;

public class MemberConfig {
    private final int ID;               // This replica's ID
    private final int N;                                    // Total number of replicas
    private final int F;                                    // Byzantine fault tolerance
    private ConcurrentHashMap<Integer, ReplicaInfo> replicas = new ConcurrentHashMap<>();
    private Set<ClientRequest> pendingCommands = ConcurrentHashMap.newKeySet(); // Commands to be executed
    private ConcurrentHashMap<Integer, Integer> ClientsLastSequence = new ConcurrentHashMap<>(); // Track last request sequence number for each client maps client port -> lastSequenceNumber
    private byte[] blsPrivateKey;
    private List<byte[]> allPublicKeys;
    private List<String> appState = new ArrayList<>();

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

    public List<String> getAppState() {
        return appState;
    }

    public void addToAppState(String command) {
        this.appState.add(command);
    }

    public int getLastSequenceForClient(int clientPort) {
        return ClientsLastSequence.getOrDefault(clientPort, 0);
    }

    public void setLastSequenceForClient(int clientPort, int sequenceNumber) {
        ClientsLastSequence.put(clientPort, sequenceNumber);
    }

    public boolean isDuplicateRequest(ClientRequest request) {
        int lastSeq = getLastSequenceForClient(request.getPort());
        return request.getSeq() <= lastSeq;
    }

    public List<Transaction> orderTransactionsForBlock(List<Transaction> transactions) {
        Map<String, Queue<Transaction>> bySender = new HashMap<>();

        for (Transaction tx : transactions) {
            bySender.computeIfAbsent(tx.getFrom(), k -> new LinkedList<>()).add(tx);
        }

        for (Queue<Transaction> queue : bySender.values()) {
            ((LinkedList<Transaction>) queue).sort((a, b) -> Long.compare(a.getNonce(), b.getNonce()));
        }

        List<Transaction> ordered = new ArrayList<>();

        while (!bySender.isEmpty() && ordered.size() < 10) { //FIXME: limit block size to 10 transactions for simplicity
            String bestSender = null;
            long highestGasPrice = -1;

            for (Map.Entry<String, Queue<Transaction>> entry : bySender.entrySet()) {
                long gasPrice = entry.getValue().peek().getGasPrice();
                if (gasPrice > highestGasPrice) {
                    highestGasPrice = gasPrice;
                    bestSender = entry.getKey();
                }
            }

            if (bestSender != null) {
                Transaction tx = bySender.get(bestSender).poll();
                ordered.add(tx);

                if (bySender.get(bestSender).isEmpty()) {
                    bySender.remove(bestSender);
                }
            }
        }

        return ordered;
    }

}

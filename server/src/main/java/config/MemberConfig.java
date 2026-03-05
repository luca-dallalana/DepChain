package config;

import java.security.PublicKey;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import info.ReplicaInfo;
import model.ClientRequest;
import threshsig.GroupKey;
import threshsig.KeyShare;

public class MemberConfig {
    private final int ID;               // This replica's ID
    private final int N;                                    // Total number of replicas
    private final int F;                                    // Byzantine fault tolerance
    private ConcurrentHashMap<Integer, ReplicaInfo> replicas = new ConcurrentHashMap<>();
    private Set<ClientRequest> pendingCommands = ConcurrentHashMap.newKeySet(); // Commands to be executed

    // Threshold signature key material
    private KeyShare keyShare;          // This replica's secret key share
    private GroupKey groupKey;          // Public verification parameters (shared by all replicas)

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

    public void initializeThresholdKeys(KeyShare keyShare, GroupKey groupKey) {
        this.keyShare = keyShare;
        this.groupKey = groupKey;
    }

    public KeyShare getKeyShare() {
        return keyShare;
    }

    public void setKeyShare(KeyShare keyShare) {
        this.keyShare = keyShare;
    }

    public GroupKey getGroupKey() {
        return groupKey;
    }

    public void setGroupKey(GroupKey groupKey) {
        this.groupKey = groupKey;
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
}

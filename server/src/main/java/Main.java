import member.DepChainMember;
import config.MemberConfig;

public class Main {
    public static void main(String[] args) {
        // Example: Start replica 0 of a 4-replica setup
        //MemberConfig config = new MemberConfig(0, 4, 1); // ID=0, N=4, f=1

        // Parse replica ID from command line
        int replicaId = 0;
        if (args.length > 0) {
            replicaId = Integer.parseInt(args[0]);
        }
        /*
        System.out.println("Starting HotStuff replica " + replicaId);
        System.out.println("Total replicas: " + config.getN());
        System.out.println("Fault tolerance: f=" + config.getF());
        System.out.println("Quorum size: " + config.getQuorumSize());

        System.out.println("Replica " + replicaId + " initialized on port "
                          + config.getReplicaInfo(replicaId).getPort());
        System.out.println("Leader for view 0: " + member.getCurrentLeader());
        */
    }
}

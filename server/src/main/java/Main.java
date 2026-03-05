import java.net.DatagramSocket;

import config.MemberConfig;
import crypto.KeyDistributor;
import member.DepChainMember;

public class Main {
    public static void main(String[] args) {
        try {
            if (args.length < 2) {
                System.err.println("Usage: java Main <replicaId> <numReplicas>");
                System.exit(1);
            }

            int thisID = Integer.parseInt(args[0]);
            int N = Integer.parseInt(args[1]);

            if (!KeyDistributor.keysExist()) {
                System.err.println("Keys not found. Run KeyGenerator first to generate keys.");
                System.exit(1);
            }

            MemberConfig config = new MemberConfig(N, thisID, null);

            KeyDistributor.KeyLoadResult keys = KeyDistributor.loadKeysForReplica(thisID);
            config.initializeThresholdKeys(keys.keyShare, keys.groupKey);

            int port = 3000 + thisID;
            DatagramSocket socket = new DatagramSocket(port);
            DepChainMember member = new DepChainMember(config, socket);
            member.start();

        } catch (Exception e) {
            System.err.println("Failed to start replica: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

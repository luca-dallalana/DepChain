import java.net.DatagramSocket;

import config.MemberConfig;
import crypto.KeyDistributor;
import member.DepChainMember;

public class Main {
    public static void main(String[] args) {
        try {
            if (args.length < 3) {
                System.err.println("Usage: java Main <replicaId> <numReplicas> <keySize>");
                System.exit(1);
            }

            int thisID = Integer.parseInt(args[0]);
            int N = Integer.parseInt(args[1]);
            int keySize = Integer.parseInt(args[2]);
            int f = (N - 1) / 3;

            if (!KeyDistributor.keysExist()) {
                KeyDistributor.generateAndSaveKeys(N, f, keySize);
            }

            MemberConfig config = new MemberConfig(N, thisID, null);

            KeyDistributor.KeyLoadResult keys = KeyDistributor.loadKeysForReplica(thisID);
            config.initializeThresholdKeys(keys.allShares, keys.groupKey);

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

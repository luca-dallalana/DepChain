import java.net.DatagramSocket;

import config.MemberConfig;
import crypto.BLSKeys;
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

            // Check if BLS keys exist, exit if not
            if (!BLSKeys.keysExist()) {
                System.err.println("Keys not found. Run: mvn exec:java -Dexec.mainClass=crypto.BLSKeys -Dexec.args=\"" + N + "\"");
                System.exit(1);
            }

            // Load BLS keys for this replica
            BLSKeys.KeySet keys = BLSKeys.loadKeys(thisID);

            // Create MemberConfig and initialize BLS keys
            MemberConfig config = new MemberConfig(N, thisID, null);
            config.initializeBLSKeys(keys.privateKey, keys.allPublicKeys);

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

package crypto;

import threshsig.GroupKey;
import threshsig.KeyShare;
import threshsig.ThresholdSigException;

import java.io.File;
import java.io.IOException;

public class KeyDistributor {

    private static final String KEY_DIR = ".depchain_keys";

    public static void generateAndSaveKeys(int n, int f, int keySize, String keyDir) throws ThresholdSigException, IOException {
        File dir = new File(keyDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        KeySetup.KeyGenerationResult result = KeySetup.generateKeys(n, f, keySize);

        String groupKeyPath = keyDir + "/groupkey.dat";
        KeySerializer.saveGroupKey(result.groupKey, groupKeyPath);

        for (int i = 0; i < n; i++) {
            String keySharePath = keyDir + "/replica_" + i + "_keyshare.dat";
            KeySerializer.saveKeyShare(result.shares[i], keySharePath);
        }
    }

    public static KeyLoadResult loadKeysForReplica(int replicaId, String keyDir) throws IOException {
        String groupKeyPath = keyDir + "/groupkey.dat";
        GroupKey groupKey = KeySerializer.loadGroupKey(groupKeyPath);

        int l = groupKey.getL();
        java.math.BigInteger delta = factorial(l);

        String keySharePath = keyDir + "/replica_" + replicaId + "_keyshare.dat";
        KeyShare myShare = KeySerializer.loadKeyShare(keySharePath, groupKey.getModulus(), delta);

        return new KeyLoadResult(groupKey, myShare);
    }

    public static boolean keysExist(String keyDir) {
        File groupKeyFile = new File(keyDir + "/groupkey.dat");
        return groupKeyFile.exists();
    }

    public static void generateAndSaveKeys(int n, int f, int keySize) throws ThresholdSigException, IOException {
        generateAndSaveKeys(n, f, keySize, KEY_DIR);
    }

    public static KeyLoadResult loadKeysForReplica(int replicaId) throws IOException {
        return loadKeysForReplica(replicaId, KEY_DIR);
    }

    public static boolean keysExist() {
        return keysExist(KEY_DIR);
    }

    private static java.math.BigInteger factorial(int n) {
        java.math.BigInteger result = java.math.BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(java.math.BigInteger.valueOf(i));
        }
        return result;
    }

    public static class KeyLoadResult {
        public final GroupKey groupKey;
        public final KeyShare keyShare;

        public KeyLoadResult(GroupKey groupKey, KeyShare keyShare) {
            this.groupKey = groupKey;
            this.keyShare = keyShare;
        }
    }
}

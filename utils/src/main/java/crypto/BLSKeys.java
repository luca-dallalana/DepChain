package crypto;

import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSecretKey;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;

import java.io.*;
import java.security.SecureRandom;
import java.util.*;

public class BLSKeys {
    private static final String KEY_DIR = "../depchain_keys";

    // Generate and save keys for n replicas
    public static void generateKeys(int n) throws IOException {
        new File(KEY_DIR).mkdirs();
        List<byte[]> publicKeys = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            BLSKeyPair keyPair = BLSKeyPair.random(new SecureRandom());

            byte[] skBytes = keyPair.getSecretKey().toBytes().toArrayUnsafe();
            byte[] pkBytes = keyPair.getPublicKey().toBytesCompressed().toArrayUnsafe();

            try (FileOutputStream fos = new FileOutputStream(KEY_DIR + "/replica_" + i + ".key")) {
                fos.write(skBytes);
            }

            publicKeys.add(pkBytes);
        }

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(KEY_DIR + "/public_keys.dat"))) {
            dos.writeInt(n);
            for (byte[] pk : publicKeys) {
                dos.write(pk);
            }
        }

        System.out.println("Generated keys for " + n + " replicas in " + KEY_DIR);
    }

    public static class KeySet {
        public final byte[] privateKey;
        public final List<byte[]> allPublicKeys;

        public KeySet(byte[] privateKey, List<byte[]> allPublicKeys) {
            this.privateKey = privateKey;
            this.allPublicKeys = allPublicKeys;
        }
    }

    // Load keys for a specific replica
    public static KeySet loadKeys(int replicaId) throws IOException {
        byte[] privateKey = new byte[32];
        try (DataInputStream dis = new DataInputStream(new FileInputStream(KEY_DIR + "/replica_" + replicaId + ".key"))) {
            dis.readFully(privateKey);
        }

        List<byte[]> allPublicKeys = new ArrayList<>();
        try (DataInputStream dis = new DataInputStream(new FileInputStream(KEY_DIR + "/public_keys.dat"))) {
            int n = dis.readInt();
            for (int i = 0; i < n; i++) {
                byte[] pk = new byte[48];
                dis.readFully(pk);
                allPublicKeys.add(pk);
            }
        }

        return new KeySet(privateKey, allPublicKeys);
    }

    public static boolean keysExist() {
        return new File(KEY_DIR, "public_keys.dat").exists();
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java BLSKeys <numReplicas>");
            System.exit(1);
        }
        generateKeys(Integer.parseInt(args[0]));
    }
}

package crypto;

import threshsig.GroupKey;
import threshsig.KeyShare;

import java.io.*;
import java.math.BigInteger;

public class KeySerializer {

    public static byte[] serializeGroupKey(GroupKey groupKey) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(groupKey.getK());
        dos.writeInt(groupKey.getL());

        byte[] nBytes = groupKey.getModulus().toByteArray();
        dos.writeInt(nBytes.length);
        dos.write(nBytes);

        byte[] eBytes = groupKey.getExponent().toByteArray();
        dos.writeInt(eBytes.length);
        dos.write(eBytes);

        dos.flush();
        return baos.toByteArray();
    }

    public static GroupKey deserializeGroupKey(byte[] data) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));

        int k = dis.readInt();
        int l = dis.readInt();

        int nLen = dis.readInt();
        byte[] nBytes = new byte[nLen];
        dis.readFully(nBytes);
        BigInteger n = new BigInteger(nBytes);

        int eLen = dis.readInt();
        byte[] eBytes = new byte[eLen];
        dis.readFully(eBytes);
        BigInteger e = new BigInteger(eBytes);

        return new GroupKey(k, l, 0, null, e, n);
    }

    public static byte[] serializeKeyShare(KeyShare share) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(share.getId());

        byte[] secretBytes = share.getSecret().toByteArray();
        dos.writeInt(secretBytes.length);
        dos.write(secretBytes);

        if (share.getVerifier() != null) {
            dos.writeBoolean(true);
            byte[] verifierBytes = share.getVerifier().toByteArray();
            dos.writeInt(verifierBytes.length);
            dos.write(verifierBytes);
        } else {
            dos.writeBoolean(false);
        }

        byte[] signValBytes = share.getSignVal().toByteArray();
        dos.writeInt(signValBytes.length);
        dos.write(signValBytes);

        dos.flush();
        return baos.toByteArray();
    }

    public static KeyShare deserializeKeyShare(byte[] data, BigInteger n, BigInteger delta) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));

        int id = dis.readInt();

        int secretLen = dis.readInt();
        byte[] secretBytes = new byte[secretLen];
        dis.readFully(secretBytes);
        BigInteger secret = new BigInteger(secretBytes);

        KeyShare share = new KeyShare(id, secret, n, delta);

        boolean hasVerifier = dis.readBoolean();
        if (hasVerifier) {
            int verifierLen = dis.readInt();
            dis.skipBytes(verifierLen);
        }

        int signValLen = dis.readInt();
        dis.skipBytes(signValLen);

        return share;
    }

    public static void saveGroupKey(GroupKey groupKey, String filePath) throws IOException {
        byte[] data = serializeGroupKey(groupKey);
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(data);
        }
    }

    public static GroupKey loadGroupKey(String filePath) throws IOException {
        File file = new File(filePath);
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(data);
        }
        return deserializeGroupKey(data);
    }

    public static void saveKeyShare(KeyShare share, String filePath) throws IOException {
        byte[] data = serializeKeyShare(share);
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(data);
        }
    }

    public static KeyShare loadKeyShare(String filePath, BigInteger n, BigInteger delta) throws IOException {
        File file = new File(filePath);
        byte[] data = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(data);
        }
        return deserializeKeyShare(data, n, delta);
    }
}

package threshsig;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;


public class SigShareSerializer {

    public static byte[] serialize(SigShare share) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(share.getId());

        byte[] sigBytes = share.getSig().toByteArray();
        dos.writeInt(sigBytes.length);
        dos.write(sigBytes);

        Verifier verifier = share.getSigVerifier();
        if (verifier != null) {
            dos.writeBoolean(true);

            byte[] zBytes = verifier.getZ().toByteArray();
            dos.writeInt(zBytes.length);
            dos.write(zBytes);

            byte[] cBytes = verifier.getC().toByteArray();
            dos.writeInt(cBytes.length);
            dos.write(cBytes);

            byte[] shareVerifierBytes = verifier.getShareVerifier().toByteArray();
            dos.writeInt(shareVerifierBytes.length);
            dos.write(shareVerifierBytes);

            byte[] groupVerifierBytes = verifier.getGroupVerifier().toByteArray();
            dos.writeInt(groupVerifierBytes.length);
            dos.write(groupVerifierBytes);
        } else {
            dos.writeBoolean(false);
        }

        dos.flush();
        return baos.toByteArray();
    }

    public static SigShare deserialize(byte[] data) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));

        int id = dis.readInt();

        int sigLen = dis.readInt();
        byte[] sigBytes = new byte[sigLen];
        dis.readFully(sigBytes);
        BigInteger sig = new BigInteger(sigBytes);

        boolean hasVerifier = dis.readBoolean();
        Verifier verifier = null;

        if (hasVerifier) {
            int zLen = dis.readInt();
            byte[] zBytes = new byte[zLen];
            dis.readFully(zBytes);
            BigInteger z = new BigInteger(zBytes);

            int cLen = dis.readInt();
            byte[] cBytes = new byte[cLen];
            dis.readFully(cBytes);
            BigInteger c = new BigInteger(cBytes);

            int shareVerifierLen = dis.readInt();
            byte[] shareVerifierBytes = new byte[shareVerifierLen];
            dis.readFully(shareVerifierBytes);
            BigInteger shareVerifier = new BigInteger(shareVerifierBytes);

            int groupVerifierLen = dis.readInt();
            byte[] groupVerifierBytes = new byte[groupVerifierLen];
            dis.readFully(groupVerifierBytes);
            BigInteger groupVerifier = new BigInteger(groupVerifierBytes);

            verifier = new Verifier(z, c, shareVerifier, groupVerifier);
        }

        return new SigShare(id, sig, verifier);
    }
}

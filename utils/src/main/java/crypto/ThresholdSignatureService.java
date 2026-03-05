package crypto;

import threshsig.GroupKey;
import threshsig.KeyShare;
import threshsig.SigShare;
import threshsig.SigShareSerializer;
import threshsig.ThresholdSigException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ThresholdSignatureService {
    private final KeyShare keyShare;
    private final GroupKey groupKey;

    public ThresholdSignatureService(KeyShare keyShare, GroupKey groupKey) {
        if (keyShare == null || groupKey == null) {
            throw new IllegalStateException("keyShare and groupKey must not be null");
        }
        this.keyShare = keyShare;
        this.groupKey = groupKey;
    }

    public byte[] createPartialSignature(int voterId, byte[] messageHash) {
        try {
            SigShare sigShare = keyShare.sign(messageHash);
            return SigShareSerializer.serialize(sigShare);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create partial signature", e);
        }
    }

    public byte[] aggregateSignatures(List<byte[]> partialSigs, byte[] messageHash) {
        try {
            // Serialize all partial signatures into a single byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            // Write count
            dos.writeInt(partialSigs.size());
            for (byte[] partialSig : partialSigs) {
                dos.writeInt(partialSig.length);
                dos.write(partialSig);
            }

            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to aggregate signatures", e);
        }
    }

    public boolean verifyAggregatedSignature(byte[] aggSig, byte[] messageHash, int quorumSize) {
        try {
            // Deserialize all partial signatures
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(aggSig));

            int count = dis.readInt();
            List<SigShare> sigShares = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                int length = dis.readInt();
                byte[] partialSig = new byte[length];
                dis.readFully(partialSig);

                SigShare sigShare = SigShareSerializer.deserialize(partialSig);
                sigShares.add(sigShare);
            }

            if (sigShares.size() < groupKey.getK()) {
                System.err.println("Insufficient signatures: " + sigShares.size() + " < " + groupKey.getK());
                return false;
            }

            SigShare[] sigArray = sigShares.toArray(new SigShare[0]);
            return SigShare.verify(
                messageHash,
                sigArray,
                groupKey.getK(),
                groupKey.getL(),
                groupKey.getModulus(),
                groupKey.getExponent()
            );
        } catch (IOException e) {
            System.err.println("Failed to deserialize aggregated signature: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (ThresholdSigException e) {
            System.err.println("Threshold signature verification failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            System.err.println("Unexpected error during signature verification: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}

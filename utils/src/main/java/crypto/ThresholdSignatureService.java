package crypto;

import supranational.blst.*;
import java.util.*;

public class ThresholdSignatureService {
    private static final String DST = "BLS_SIG_BLS12381G2_XMD:SHA-256_SSWU_RO_NUL_";

    private final SecretKey privateKey;
    private final List<byte[]> allPublicKeys;

    public ThresholdSignatureService(byte[] privateKeyBytes, List<byte[]> allPublicKeys) {
        this.privateKey = new SecretKey();
        this.privateKey.from_lendian(privateKeyBytes);
        this.allPublicKeys = allPublicKeys;
    }

    public byte[] createPartialSignature(int voterId, byte[] messageHash) {
        P2 sig = new P2().hash_to(messageHash, DST).sign_with(privateKey);
        return sig.compress();
    }

    public byte[] aggregateSignatures(List<byte[]> partialSigs, byte[] messageHash) {
        System.out.println("[BLS] Aggregating " + partialSigs.size() + " signatures");
        P2 aggregated = new P2_Affine(partialSigs.get(0)).to_jacobian();
        for (int i = 1; i < partialSigs.size(); i++) {
            System.out.println("[BLS] Adding signature " + i);
            aggregated.aggregate(new P2_Affine(partialSigs.get(i)));
        }
        return aggregated.compress();
    }

    public boolean verifyPartialSignature(byte[] partialSig, byte[] messageHash, int senderId) {
        try {
            P1_Affine pk = new P1_Affine(allPublicKeys.get(senderId));
            P2_Affine sig = new P2_Affine(partialSig);
            return pk.core_verify(sig, true, messageHash, DST, null) == BLST_ERROR.BLST_SUCCESS;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean verifyAggregatedSignature(byte[] aggSig, byte[] messageHash, List<Integer> signers) {
        try {
            System.out.println("[BLS] Verifying with signers: " + signers);
            System.out.println("[BLS] Total public keys available: " + allPublicKeys.size());

            // Aggregate public keys in same order as signatures (forward order)
            P1 aggregatedPk = new P1_Affine(allPublicKeys.get(signers.get(0))).to_jacobian();
            for (int i = 1; i < signers.size(); i++) {
                System.out.println("[BLS] Aggregating public key for signer: " + signers.get(i));
                aggregatedPk.aggregate(new P1_Affine(allPublicKeys.get(signers.get(i))));
            }

            P2_Affine sig = new P2_Affine(aggSig);
            boolean result = aggregatedPk.to_affine().core_verify(sig, true, messageHash, DST, null) == BLST_ERROR.BLST_SUCCESS;
            System.out.println("[BLS] Verification result: " + result);
            return result;
        } catch (Exception e) {
            System.err.println("[BLS] Error during aggregated signature verification: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}

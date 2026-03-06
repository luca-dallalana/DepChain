package crypto;

import supranational.blst.*;
import java.util.*;

public class ThresholdSignatureService {
    private static final String DST = "BLS_SIG_BLS12381G2_XMD:SHA-256_SSWU_RO_NUL_";

    private final SecretKey privateKey;
    private final List<byte[]> allPublicKeys;

    public ThresholdSignatureService(byte[] privateKeyBytes, List<byte[]> allPublicKeys) {
        this.privateKey = new SecretKey();
        this.privateKey.from_bendian(privateKeyBytes);
        this.allPublicKeys = allPublicKeys;
    }

    public byte[] createPartialSignature(int voterId, byte[] messageHash) {
        P2 sig = new P2().hash_to(messageHash, DST).sign_with(privateKey);
        return sig.compress();
    }

    public byte[] aggregateSignatures(List<byte[]> partialSigs, byte[] messageHash) {
        P2 aggregated = new P2_Affine(partialSigs.get(0)).to_jacobian();
        for (int i = 1; i < partialSigs.size(); i++) {
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

    public boolean verifyAggregatedSignature(byte[] aggSig, byte[] messageHash, int quorumSize) {
        try {
            P1 aggregatedPk = new P1_Affine(allPublicKeys.get(0)).to_jacobian();
            for (int i = 1; i < quorumSize; i++) {
                aggregatedPk.aggregate(new P1_Affine(allPublicKeys.get(i)));
            }
            P2_Affine sig = new P2_Affine(aggSig);
            return aggregatedPk.to_affine().core_verify(sig, true, messageHash, DST, null) == BLST_ERROR.BLST_SUCCESS;
        } catch (Exception e) {
            return false;
        }
    }
}

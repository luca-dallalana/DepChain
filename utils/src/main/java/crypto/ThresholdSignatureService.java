package crypto;

import supranational.blst.*;
import tech.pegasys.teku.bls.impl.blst.JBlst;
import java.util.*;

public class ThresholdSignatureService {
    private static final String DST = "BLS_SIG_BLS12381G2_XMD:SHA-256_SSWU_RO_NUL_";

    private final scalar privateKey;
    private final List<byte[]> allPublicKeys;

    static {
        JBlst.loadNativeLibrary();
    }

    public ThresholdSignatureService(byte[] privateKeyBytes, List<byte[]> allPublicKeys) {
        this.privateKey = new scalar();
        blst.scalar_from_bendian(this.privateKey, privateKeyBytes);
        this.allPublicKeys = allPublicKeys;
    }

    public byte[] createPartialSignature(int voterId, byte[] messageHash) {
        p2 sig = new p2();
        blst.sign_pk2_in_g1(sig, null, messageHash, DST.getBytes(), privateKey);

        byte[] serialized = new byte[blst.P2_COMPRESSED_BYTES];
        blst.p2_affine_compress(serialized, sig.to_affine());
        return serialized;
    }

    public byte[] aggregateSignatures(List<byte[]> partialSigs, byte[] messageHash) {
        p2 aggregated = new p2();
        p2_affine point = new p2_affine();
        blst.p2_uncompress(point, partialSigs.get(0));
        blst.p2_from_affine(aggregated, point);

        for (int i = 1; i < partialSigs.size(); i++) {
            blst.p2_uncompress(point, partialSigs.get(i));
            blst.p2_add_or_double_affine(aggregated, aggregated, point);
        }

        byte[] result = new byte[blst.P2_COMPRESSED_BYTES];
        blst.p2_affine_compress(result, aggregated.to_affine());
        return result;
    }

    public boolean verifyPartialSignature(byte[] partialSig, byte[] messageHash, int senderId) {
        try {
            p1_affine pk = new p1_affine();
            blst.p1_uncompress(pk, allPublicKeys.get(senderId));

            p2_affine sig = new p2_affine();
            blst.p2_uncompress(sig, partialSig);

            return blst.core_verify_pk_in_g1(pk, sig, true, messageHash, DST.getBytes(), null) == BLST_ERROR.BLST_SUCCESS;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean verifyAggregatedSignature(byte[] aggSig, byte[] messageHash, int quorumSize) {
        try {
            p1 aggregatedPk = new p1();
            p1_affine pkAffine = new p1_affine();
            blst.p1_uncompress(pkAffine, allPublicKeys.get(0));
            blst.p1_from_affine(aggregatedPk, pkAffine);

            for (int i = 1; i < quorumSize; i++) {
                blst.p1_uncompress(pkAffine, allPublicKeys.get(i));
                blst.p1_add_or_double_affine(aggregatedPk, aggregatedPk, pkAffine);
            }

            p2_affine sig = new p2_affine();
            blst.p2_uncompress(sig, aggSig);

            return blst.core_verify_pk_in_g1(aggregatedPk.to_affine(), sig, true, messageHash, DST.getBytes(), null) == BLST_ERROR.BLST_SUCCESS;
        } catch (Exception e) {
            return false;
        }
    }
}

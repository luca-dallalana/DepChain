package crypto;

import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.bls.BLSSignature;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;

import java.util.List;
import java.util.stream.Collectors;

public class ThresholdSignatureService {

    private final BLSSecretKey privateKey;
    private final List<BLSPublicKey> publicKeys;

    public ThresholdSignatureService(byte[] privateKeyBytes, List<byte[]> allPublicKeys) {
        this.privateKey = BLSSecretKey.fromBytes(Bytes32.wrap(privateKeyBytes));
        this.publicKeys = allPublicKeys.stream()
                .map(pk -> BLSPublicKey.fromBytesCompressed(Bytes48.wrap(pk)))
                .collect(Collectors.toList());
    }

    public byte[] createPartialSignature(int voterId, byte[] messageHash) {
        return BLS.sign(privateKey, Bytes.wrap(messageHash)).toBytesCompressed().toArrayUnsafe();
    }

    public byte[] aggregateSignatures(List<byte[]> partialSigs) {
        List<BLSSignature> signatures = partialSigs.stream()
                .map(s -> BLSSignature.fromBytesCompressed(Bytes.wrap(s)))
                .collect(Collectors.toList());
        return BLS.aggregate(signatures).toBytesCompressed().toArrayUnsafe();
    }

    public boolean verifyPartialSignature(byte[] partialSig, byte[] messageHash, int senderId) {
        try {
            BLSSignature sig = BLSSignature.fromBytesCompressed(Bytes.wrap(partialSig));
            return BLS.verify(publicKeys.get(senderId), Bytes.wrap(messageHash), sig);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean verifyAggregatedSignature(byte[] aggSig, byte[] messageHash, List<Integer> signers) {
        try {
            List<BLSPublicKey> signerKeys = signers.stream()
                    .map(publicKeys::get)
                    .collect(Collectors.toList());
            BLSSignature signature = BLSSignature.fromBytesCompressed(Bytes.wrap(aggSig));
            return BLS.fastAggregateVerify(signerKeys, Bytes.wrap(messageHash), signature);
        } catch (Exception e) {
            System.err.println("[BLS] Verification error: " + e.getMessage());
            return false;
        }
    }
}

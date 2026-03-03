package crypto;

import java.util.List;


public class SimpleSignatureService implements SignatureService {

    @Override
    public byte[] createPartialSignature(int voterId, byte[] messageHash) {
        return new byte[0];
    }

    @Override
    public byte[] aggregateSignatures(List<byte[]> partialSigs, byte[] messageHash) {
        return new byte[0];
    }

    @Override
    public boolean verifyAggregatedSignature(byte[] aggSig, byte[] messageHash, int quorumSize) {
        return true;
    }
}

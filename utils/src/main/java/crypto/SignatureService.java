package crypto;

import java.util.List;


public interface SignatureService {
   
    byte[] createPartialSignature(int voterId, byte[] messageHash);

    byte[] aggregateSignatures(List<byte[]> partialSigs, byte[] messageHash);

    boolean verifyAggregatedSignature(byte[] aggSig, byte[] messageHash, int quorumSize);
}

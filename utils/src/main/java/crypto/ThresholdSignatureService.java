package crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ThresholdSignatureService {

    public ThresholdSignatureService() {
    }

    public byte[] createPartialSignature(int voterId, byte[] messageHash) {
        return new byte[0];
    }

    public byte[] aggregateSignatures(List<byte[]> partialSigs, byte[] messageHash) {
        return new byte[0];
    }

    public boolean verifyPartialSignature(byte[] partialSig, byte[] messageHash, int senderId) {
        return true;
    }

    public boolean verifyAggregatedSignature(byte[] aggSig, byte[] messageHash, int quorumSize) {
        return true;
    }
}

package model;

import java.util.List;

public class QC {
    public String type;
    public int viewNumber;
    public String blockHash; // Hash of the block this QC certifies
    public byte[] sig;
    public List<Integer> signers; // key indices of replicas that contributed to this QC

    public QC(String type, int viewNumber, String blockHash, byte[] sig) {
        this.type = type;
        this.viewNumber = viewNumber;
        this.blockHash = blockHash;
        this.sig = sig;
    }
}

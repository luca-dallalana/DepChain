package model;

import java.util.List;

public class QC {
    public String type;
    public int viewNumber;
    public Node node;
    public byte[] sig;
    public List<Integer> signers; // key indices of replicas that contributed to this QC

    public QC(String type, int viewNumber, Node node, byte[] sig) {
        this.type = type;
        this.viewNumber = viewNumber;
        this.node = node;
        this.sig = sig;
    }
}

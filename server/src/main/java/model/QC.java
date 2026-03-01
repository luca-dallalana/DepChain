package model;

public class QC {
    public String type;
    public int viewNumber;
    public Node node;
    public byte[] sig;

    public QC(String type, int viewNumber, Node node, byte[] sig) {
        this.type = type;
        this.viewNumber = viewNumber;
        this.node = node;
        this.sig = sig;
    }
}

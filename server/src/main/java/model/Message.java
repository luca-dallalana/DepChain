package model;

public final class Message {
    public String  type;         // new-view, prepare, pre-commit, commit, decide
    public int     viewNumber;   // sender's current view
    public Node    node;         // the proposed node
    public QC      justify;
    public byte[]  partialSig;
    public int     senderPort;
}

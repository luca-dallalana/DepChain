package model;

import java.util.List;

public class CatchUp {
    public int        viewNumber;
    public QC         lockedQC;
    public Node       receivedNode; // the node that the sender of the catch-up message is currently at (the one he sent me in justify)
    public List<Node> nodeList;
    public int        senderPort;
}

package model;

import blockchain.Block;
import java.util.List;

public class CatchUp {
    public int         viewNumber;
    public QC          lockedQC;
    public String      receivedBlockHash; // the block hash that the sender of the catch-up message is currently at (the one he sent me in justify)
    public List<Block> blockList;
    public int         senderPort;
}

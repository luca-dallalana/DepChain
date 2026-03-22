package model;

import blockchain.Block;

public final class Message {
    public String  type;         // new-view, prepare, pre-commit, commit, decide
    public int     viewNumber;   // sender's current view
    public Block   block;        // the proposed block
    public String  blockHash;    // the hash of the proposed block
    public QC      justify;      
    public byte[]  partialSig;
    public int     senderPort;
}

package model;

import blockchain.Transaction;
import java.util.List;

public final class Message {
    public String            type;         // new-view, prepare, pre-commit, commit, decide
    public int               viewNumber;   // sender's current view
    public List<Transaction> transactions; // transactions included in the proposed block
    public String            blockHash;    // the hash of the proposed block
    public QC                justify;      
    public byte[]            partialSig;
    public int               senderPort;
}

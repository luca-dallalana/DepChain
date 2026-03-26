package model;

import blockchain.Transaction;
import java.util.List;

public class CatchUp {
    public int                     viewNumber;
    public QC                      lockedQC;
    public String                  receivedBlockHash;
    public List<List<Transaction>> transactionList;
    public List<String>            blockHashList;
    public int                     senderPort;
}

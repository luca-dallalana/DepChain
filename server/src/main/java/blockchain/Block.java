package blockchain;

import java.util.ArrayList;
import java.util.List;

public class Block {
    public String blockHash;            // Block hash (hex)
    public String parentBlockHash;    // Previous block (null for genesis)
    public List<Transaction> transactions;
    public WorldState state;            // World state after executing txs
    public long timestamp;
    public long blockNumber;            // Height in blockchain

    public Block(String blockHash, String parentBlockHash,
                List<Transaction> transactions, WorldState state,
                long timestamp, long blockNumber) {
        this.blockHash = blockHash;
        this.parentBlockHash = parentBlockHash;
        this.transactions = transactions != null ? transactions : new ArrayList<>();
        this.state = state;
        this.timestamp = timestamp;
        this.blockNumber = blockNumber;
    }

    public Block() {
        this.transactions = new ArrayList<>();
        this.state = new WorldState();
    }

    public boolean isGenesisBlock() {
        return blockNumber == 0 && parentBlockHash == null;
    }

    public static Block createGenesis() {
        return new Block(
            null,  // blockHash - calculate later
            null,  // parentBlockHash - null for genesis
            new ArrayList<>(),
            new WorldState(),
            System.currentTimeMillis(),
            0  // blockNumber 0
        );
    }
}

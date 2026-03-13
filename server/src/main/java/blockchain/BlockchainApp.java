package blockchain;

import java.util.List;

public interface BlockchainApp {
    // Consensus calls this when a block is decided
    void executeBlock(Block block);

    // Consensus calls this to get the next block to propose
    Block buildBlock(List<Transaction> pendingTxs);

    // Consensus calls this to validate a proposed block before voting
    boolean isValidBlock(Block block);
}

package blockchain;

import java.util.concurrent.ConcurrentHashMap;

public class BlockStore {
    private ConcurrentHashMap<String, Block> blockStore;

    public BlockStore(Block genesisBlock) {
        this.blockStore = new ConcurrentHashMap<>();
        this.blockStore.put(genesisBlock.blockHash, genesisBlock);
    }

    public void storeBlock(Block block) {
        blockStore.put(block.blockHash, block);
    }

    public Block getBlockByHash(String blockHash) {
        return blockStore.get(blockHash);
    }
}

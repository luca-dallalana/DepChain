package blockchain;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class BlockStore {
    private ConcurrentHashMap<String, Block> blockStore;
    private Block firstBlock;

    public BlockStore(Block genesisBlock) {
        this.blockStore = new ConcurrentHashMap<>();
        this.firstBlock = genesisBlock;
        storeBlock(genesisBlock);
    }

    public void storeBlock(Block block) {
        if (block == null) {
            return;
        }

        try {
            String hash = block.blockHash;
            if (hash == null || hash.isBlank()) {
                hash = block.depHash();
                block.blockHash = hash;
            }
            blockStore.put(hash, block);
        } catch (Exception e) {
            throw new RuntimeException("Failed to store block", e);
        }
    }

    public Block getBlockByHash(String blockHash) {
        return blockStore.get(blockHash);
    }

    public Block getFirstBlock() {
        return firstBlock;
    }

    // Check if descendant extends from ancestor in the block tree.
    public boolean extendsFrom(Block descendant, String ancestorHash) {
        try {  
            if (descendant == null || ancestorHash == null) {
                return false;
            }

            // Traverse up the tree from descendant
            Block current = descendant;
            while (current != null && current.parentBlockHash != null) {
                // Get parent from storage
                Block parent = getBlockByHash(current.parentBlockHash);
                if (parent == null) {
                    return false;
                }

                // Check if parent matches ancestor
                String parentHash = parent.depHash();
                if (parentHash.equals(ancestorHash)) {
                    return true;
                }
                current = parent;
            }

            return false;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<Block> getBlocksUntil(String startBlockHash, String endBlockHash) {
        Block block = getBlockByHash(startBlockHash);
        long endHeight = getBlockByHash(endBlockHash).blockNumber;
        List<Block> blockList = new ArrayList<>();
        Block nextBlock = block;

        while (true) {
            if (nextBlock == null) {
                break; // No more nodes to execute
            }

            if (nextBlock.blockNumber <= endHeight) {
                break; // Already executed
            }

            blockList.add(nextBlock);
            nextBlock = getBlockByHash(nextBlock.parentBlockHash);
        }

        return blockList;
    }
}

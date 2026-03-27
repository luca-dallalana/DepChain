package blockchain;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import blockchain.evm.EVMHelper;

public class BlockStorePruneTest {

    private BlockStore blockStore;
    private Block genesisBlock;
    private Block block1;
    private Block block2;
    private Block block3LockedQC;
    private Block orphanBlock1;
    private Block orphanBlock2;
    private WorldState genesisState;
    private Address client0;
    private Address client1;

    @BeforeEach
    public void setup() {
        System.out.println("\n=== BlockStore Prune Test Setup ===\n");

        String projectRoot = "..";

        // Clean up existing genesis file
        String genesisPath = projectRoot + "/blockchain_data/genesis_block.json";
        try {
            Files.deleteIfExists(Paths.get(genesisPath));
        } catch (Exception e) {
            // Ignore
        }

        // Create genesis block
        genesisBlock = Block.createAndSaveGenesis(projectRoot);
        genesisState = genesisBlock.state;

        // Get client addresses for creating transactions
        String client0Addr = AddressUtils.generateAddressFromPublicKey(projectRoot + "/rsa_keys/client_0/client_0.pubkey");
        String client1Addr = AddressUtils.generateAddressFromPublicKey(projectRoot + "/rsa_keys/client_1/client_1.pubkey");
        client0 = Address.fromHexString(client0Addr);
        client1 = Address.fromHexString(client1Addr);

        System.out.println("Genesis block created: " + genesisBlock.blockHash);
        System.out.println("Genesis block height: " + genesisBlock.blockNumber);

        // Initialize BlockStore with genesis
        blockStore = new BlockStore(genesisBlock);
        System.out.println("BlockStore initialized with genesis block\n");
    }

    /**
     * Creates a simple transaction for testing block creation
     */
    private Transaction createSimpleTransaction(Address from, Address to, long value) {
        return new Transaction(
            -1,
            from,
            to,
            value,
            new byte[0],  // Empty data for native transfer
            21000,        // Gas limit for native transfer
            1,            // Gas price
            0,            // nonce
            null          // No signature for testing
        );
    }

    /**
     * Builds a new block on top of a parent block with given transactions
     */
    private Block buildBlockOnParent(Block parent, List<Transaction> transactions) throws Exception {
        EVMHelper evm = new EVMHelper();
        WorldState newState = BlockchainMember.computeState(evm, transactions, parent.state);
        Block newBlock = Block.createLeaf(parent, transactions, newState);
        return newBlock;
    }

    @Test
    public void testPruneToLockedSubtree() throws Exception {
        System.out.println("=== Test: Prune to Locked Subtree (Prune 2 Blocks, LockedQC at Height 3) ===\n");

        // === Build the MAIN CHAIN: genesis -> block1 -> block2 -> block3 (locked QC) ===
        System.out.println("--- Building Main Chain ---");

        // Block 1 (height 1): Transfer from client0 to client1
        List<Transaction> txBlock1 = new ArrayList<>();
        txBlock1.add(createSimpleTransaction(client0, client1, 500));
        block1 = buildBlockOnParent(genesisBlock, txBlock1);
        blockStore.storeBlock(block1);
        System.out.println("Block 1 created: height=" + block1.blockNumber + ", hash=" + block1.blockHash);

        // Block 2 (height 2): Transfer from client1 to client0
        List<Transaction> txBlock2 = new ArrayList<>();
        txBlock2.add(createSimpleTransaction(client1, client0, 200));
        block2 = buildBlockOnParent(block1, txBlock2);
        blockStore.storeBlock(block2);
        System.out.println("Block 2 created: height=" + block2.blockNumber + ", hash=" + block2.blockHash);

        // Block 3 (height 3): Transfer from client0 to client1 (THIS WILL BE THE LOCKED QC BLOCK)
        List<Transaction> txBlock3 = new ArrayList<>();
        txBlock3.add(createSimpleTransaction(client0, client1, 300));
        block3LockedQC = buildBlockOnParent(block2, txBlock3);
        blockStore.storeBlock(block3LockedQC);
        System.out.println("Block 3 (Locked QC) created: height=" + block3LockedQC.blockNumber + ", hash=" + block3LockedQC.blockHash);

        // === Build ORPHAN BLOCKS (NOT in the main chain, will be pruned) ===
        System.out.println("\n--- Building Orphan Chain (to be pruned) ---");

        // Orphan Block 1 (height 1, but branches from genesis): Different from block1
        // This creates a fork at genesis -> orphanBlock1
        List<Transaction> txOrphan1 = new ArrayList<>();
        txOrphan1.add(createSimpleTransaction(client1, client0, 100));
        orphanBlock1 = buildBlockOnParent(genesisBlock, txOrphan1);
        blockStore.storeBlock(orphanBlock1);
        System.out.println("Orphan Block 1 created: height=" + orphanBlock1.blockNumber + ", hash=" + orphanBlock1.blockHash);

        // Orphan Block 2 (height 2, built on orphanBlock1): Not part of main chain
        List<Transaction> txOrphan2 = new ArrayList<>();
        txOrphan2.add(createSimpleTransaction(client0, client1, 150));
        orphanBlock2 = buildBlockOnParent(orphanBlock1, txOrphan2);
        blockStore.storeBlock(orphanBlock2);
        System.out.println("Orphan Block 2 created: height=" + orphanBlock2.blockNumber + ", hash=" + orphanBlock2.blockHash);

        // === Print BlockStore state BEFORE pruning ===
        System.out.println("\n--- BlockStore State BEFORE Pruning ---");
        System.out.println("Total blocks before prune: 6");
        System.out.println("  - Genesis (height 0)");
        System.out.println("  - Block 1 (height 1, main chain)");
        System.out.println("  - Block 2 (height 2, main chain)");
        System.out.println("  - Block 3 (height 3, main chain, LOCKED QC)");
        System.out.println("  - Orphan Block 1 (height 1, fork)");
        System.out.println("  - Orphan Block 2 (height 2, fork)");

        // === Verify all blocks are in store ===
        assertNotNull(blockStore.getBlockByHash(genesisBlock.blockHash), "Genesis should be in store");
        assertNotNull(blockStore.getBlockByHash(block1.blockHash), "Block 1 should be in store");
        assertNotNull(blockStore.getBlockByHash(block2.blockHash), "Block 2 should be in store");
        assertNotNull(blockStore.getBlockByHash(block3LockedQC.blockHash), "Block 3 should be in store");
        assertNotNull(blockStore.getBlockByHash(orphanBlock1.blockHash), "Orphan Block 1 should be in store");
        assertNotNull(blockStore.getBlockByHash(orphanBlock2.blockHash), "Orphan Block 2 should be in store");
        System.out.println(" All blocks verified in store before pruning");

        // === Execute the prune operation ===
        System.out.println("\n--- Executing Prune Operation ---");
        System.out.println("Pruning to locked subtree rooted at Block 3 (height 3): " + block3LockedQC.blockHash);
        int prunedCount = blockStore.pruneToLockedSubtree(block3LockedQC.blockHash);
        System.out.println(" Pruning completed. Blocks pruned: " + prunedCount);

        // === Verify pruning results ===
        System.out.println("\n--- BlockStore State AFTER Pruning ---");
        System.out.println("Blocks pruned: " + prunedCount);
        System.out.println("Expected blocks remaining: 4 (genesis, block1, block2, block3)");

        assertEquals(2, prunedCount, "Exactly 2 blocks should have been pruned (orphan blocks)");
        System.out.println(" Correct number of blocks pruned (2)");

        // === Verify main chain blocks remain ===
        System.out.println("\n--- Verifying Main Chain Blocks Still Exist ---");
        assertNotNull(blockStore.getBlockByHash(genesisBlock.blockHash), "Genesis should still be in store");
        System.out.println(" Genesis block exists");

        assertNotNull(blockStore.getBlockByHash(block1.blockHash), "Block 1 should still be in store");
        System.out.println(" Block 1 exists");

        assertNotNull(blockStore.getBlockByHash(block2.blockHash), "Block 2 should still be in store");
        System.out.println(" Block 2 exists");

        assertNotNull(blockStore.getBlockByHash(block3LockedQC.blockHash), "Block 3 (locked QC) should still be in store");
        System.out.println(" Block 3 (Locked QC) exists");

        // === Verify orphan blocks are removed ===
        System.out.println("\n--- Verifying Orphan Blocks Are Removed ---");
        assertTrue(blockStore.getBlockByHash(orphanBlock1.blockHash) == null, "Orphan Block 1 should be removed");
        System.out.println(" Orphan Block 1 was removed");

        assertTrue(blockStore.getBlockByHash(orphanBlock2.blockHash) == null, "Orphan Block 2 should be removed");
        System.out.println(" Orphan Block 2 was removed");

        // === Verify block chain integrity ===
        System.out.println("\n--- Verifying Block Chain Integrity ---");
        assertEquals(block3LockedQC.parentBlockHash, block2.blockHash, "Block 3 should reference Block 2 as parent");
        assertEquals(block2.parentBlockHash, block1.blockHash, "Block 2 should reference Block 1 as parent");
        assertEquals(block1.parentBlockHash, genesisBlock.blockHash, "Block 1 should reference Genesis as parent");
        System.out.println(" Chain integrity verified: Genesis -> B1 -> B2 -> B3(LockedQC)");

        // === Verify block heights ===
        System.out.println("\n--- Verifying Block Heights ---");
        assertEquals(0, genesisBlock.blockNumber, "Genesis should be at height 0");
        assertEquals(1, block1.blockNumber, "Block 1 should be at height 1");
        assertEquals(2, block2.blockNumber, "Block 2 should be at height 2");
        assertEquals(3, block3LockedQC.blockNumber, "Block 3 should be at height 3 (locked QC)");
        System.out.println(" All block heights correct: 0, 1, 2, 3");

        System.out.println("\n=== Test PASSED: Prune logic works correctly ===\n");
    }

    @Test
    public void testPruneRemovesAlternativeFork() throws Exception {
        System.out.println("=== Test: Prune Removes Alternative Fork ===\n");

        // Build two competing forks from the same parent:
        //                  ├─ Block 3 (height 3, LOCKED) ← main chain
        // Genesis -> B1 -> B2 <
        //                  └─ Block 4 (height 3, alternative fork)
        //                      └─ Block 5 (height 4, extending fork)
        //
        // Expected: After pruning to B3 (locked), B4 and B5 should be removed
        // because they're on an alternative fork that doesn't have the new locked block
        
        System.out.println("--- Building Main Chain Backbone ---");
        List<Transaction> txs1 = new ArrayList<>();
        txs1.add(createSimpleTransaction(client0, client1, 100));
        block1 = buildBlockOnParent(genesisBlock, txs1);
        blockStore.storeBlock(block1);
        System.out.println("Block 1 (height 1) created: " + block1.blockHash);

        List<Transaction> txs2 = new ArrayList<>();
        txs2.add(createSimpleTransaction(client1, client0, 50));
        block2 = buildBlockOnParent(block1, txs2);
        blockStore.storeBlock(block2);
        System.out.println("Block 2 (height 2) created: " + block2.blockHash);

        System.out.println("\n--- Building Main Chain Fork (B3 - LOCKED) ---");
        List<Transaction> txs3 = new ArrayList<>();
        txs3.add(createSimpleTransaction(client0, client1, 75));
        block3LockedQC = buildBlockOnParent(block2, txs3);
        blockStore.storeBlock(block3LockedQC);
        System.out.println("Block 3 (height 3, LOCKED) created: " + block3LockedQC.blockHash);

        System.out.println("\n--- Building Alternative Fork (B4 - competing with B3 at height 3) ---");
        List<Transaction> txs4 = new ArrayList<>();
        txs4.add(createSimpleTransaction(client1, client0, 25));
        Block block4 = buildBlockOnParent(block2, txs4);  // Also extends from block2, competing with B3
        blockStore.storeBlock(block4);
        System.out.println("Block 4 (height 3, alternative fork) created: " + block4.blockHash);

        System.out.println("\n--- Extending Alternative Fork (B5 from B4) ---");
        List<Transaction> txs5 = new ArrayList<>();
        txs5.add(createSimpleTransaction(client0, client1, 60));
        Block block5 = buildBlockOnParent(block4, txs5);  // Extends the alternative fork
        blockStore.storeBlock(block5);
        System.out.println("Block 5 (height 4, extending alternative fork) created: " + block5.blockHash);

        System.out.println("\n--- BlockStore State BEFORE Pruning ---");
        System.out.println("Total blocks: 6");
        System.out.println("  Main chain: Genesis (0) -> B1 (1) -> B2 (2) -> B3 (3, LOCKED) ");
        System.out.println("  Alternative fork: B2 (2) -> B4 (3) -> B5 (4)");

        int prunedCount = blockStore.pruneToLockedSubtree(block3LockedQC.blockHash);

        System.out.println("\n--- BlockStore State AFTER Pruning ---");
        System.out.println("Blocks pruned: " + prunedCount);
        assertEquals(2, prunedCount, "Blocks B4 and B5 on the alternative fork should be pruned");
        System.out.println("Expected blocks remaining: 4 (Genesis, B1, B2, B3)");

        // Verify main chain blocks still exist
        System.out.println("\n--- Verifying Main Chain Blocks ---");
        assertNotNull(blockStore.getBlockByHash(genesisBlock.blockHash), "Genesis should exist");
        System.out.println(" Genesis exists");
        
        assertNotNull(blockStore.getBlockByHash(block1.blockHash), "Block 1 should exist");
        System.out.println(" Block 1 exists");
        
        assertNotNull(blockStore.getBlockByHash(block2.blockHash), "Block 2 (fork point) should exist");
        System.out.println(" Block 2 (fork point) exists");
        
        assertNotNull(blockStore.getBlockByHash(block3LockedQC.blockHash), "Block 3 (locked) should exist");
        System.out.println(" Block 3 (Locked) exists");

        // Verify alternative fork blocks are removed
        System.out.println("\n--- Verifying Alternative Fork Blocks Removed ---");
        assertTrue(blockStore.getBlockByHash(block4.blockHash) == null, "Block 4 (alternative fork) should be removed");
        System.out.println(" Block 4 (alternative fork) removed");
        
        assertTrue(blockStore.getBlockByHash(block5.blockHash) == null, "Block 5 (extending alternative fork) should be removed");
        System.out.println(" Block 5 (extending alternative fork) removed");

        System.out.println("\n=== Test PASSED: Alternative fork correctly pruned ===\n");
    }
}


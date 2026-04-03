package blockchain;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.hyperledger.besu.datatypes.Address;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import blockchain.evm.EVMHelper;

public class BlockStorePruneTest {

    private BlockStore blockStore;
    private Block genesisBlock;
    private Address client0;
    private Address client1;

    @BeforeEach
    public void setup() {
        String projectRoot = "..";

        try {
            Files.deleteIfExists(Paths.get(projectRoot + "/blockchain_data/genesis_block.json"));
        } catch (Exception e) {
        }

        genesisBlock = Block.createAndSaveGenesis(projectRoot);

        String client0Addr = AddressUtils.generateAddressFromPublicKey(projectRoot + "/rsa_keys/client_0/client_0.pubkey");
        String client1Addr = AddressUtils.generateAddressFromPublicKey(projectRoot + "/rsa_keys/client_1/client_1.pubkey");
        client0 = Address.fromHexString(client0Addr);
        client1 = Address.fromHexString(client1Addr);

        blockStore = new BlockStore(genesisBlock);
    }

    private Transaction createTransaction(Address from, Address to, long value) {
        return new Transaction(-1, from, to, value, new byte[0], 21000, 1, 0, null);
    }

    private Block buildBlock(Block parent, List<Transaction> transactions) throws Exception {
        EVMHelper evm = new EVMHelper();
        WorldState newState = BlockchainMember.computeState(evm, transactions, parent.state);
        return Block.createLeaf(parent, transactions, newState);
    }

    @Test
    public void testPruneKeepsRecentBlocks() throws Exception {
        List<Transaction> txs1 = new ArrayList<>();
        txs1.add(createTransaction(client0, client1, 500));
        Block block1 = buildBlock(genesisBlock, txs1);
        blockStore.storeBlock(block1);

        List<Transaction> txs2 = new ArrayList<>();
        txs2.add(createTransaction(client1, client0, 200));
        Block block2 = buildBlock(block1, txs2);
        blockStore.storeBlock(block2);

        List<Transaction> txs3 = new ArrayList<>();
        txs3.add(createTransaction(client0, client1, 300));
        Block block3 = buildBlock(block2, txs3);
        blockStore.storeBlock(block3);

        List<Transaction> orphanTxs1 = new ArrayList<>();
        orphanTxs1.add(createTransaction(client1, client0, 100));
        Block orphanBlock1 = buildBlock(genesisBlock, orphanTxs1);
        blockStore.storeBlock(orphanBlock1);

        List<Transaction> orphanTxs2 = new ArrayList<>();
        orphanTxs2.add(createTransaction(client0, client1, 150));
        Block orphanBlock2 = buildBlock(orphanBlock1, orphanTxs2);
        blockStore.storeBlock(orphanBlock2);

        int prunedCount = blockStore.pruneToLockedSubtree(block3.blockHash);

        assertEquals(2, prunedCount);
        assertNotNull(blockStore.getBlockByHash(genesisBlock.blockHash));
        assertNotNull(blockStore.getBlockByHash(block1.blockHash));
        assertNotNull(blockStore.getBlockByHash(block2.blockHash));
        assertNotNull(blockStore.getBlockByHash(block3.blockHash));
        assertNull(blockStore.getBlockByHash(orphanBlock1.blockHash));
        assertNull(blockStore.getBlockByHash(orphanBlock2.blockHash));
    }

    @Test
    public void testPruneRemovesOldBlocks() throws Exception {
        List<Transaction> txs1 = new ArrayList<>();
        txs1.add(createTransaction(client0, client1, 100));
        Block block1 = buildBlock(genesisBlock, txs1);
        blockStore.storeBlock(block1);

        List<Transaction> txs2 = new ArrayList<>();
        txs2.add(createTransaction(client1, client0, 50));
        Block block2 = buildBlock(block1, txs2);
        blockStore.storeBlock(block2);

        List<Transaction> txs3 = new ArrayList<>();
        txs3.add(createTransaction(client0, client1, 75));
        Block block3 = buildBlock(block2, txs3);
        blockStore.storeBlock(block3);

        List<Transaction> txs4 = new ArrayList<>();
        txs4.add(createTransaction(client1, client0, 25));
        Block block4 = buildBlock(block2, txs4);
        blockStore.storeBlock(block4);

        List<Transaction> txs5 = new ArrayList<>();
        txs5.add(createTransaction(client0, client1, 60));
        Block block5 = buildBlock(block4, txs5);
        blockStore.storeBlock(block5);

        int prunedCount = blockStore.pruneToLockedSubtree(block3.blockHash);

        assertEquals(2, prunedCount);
        assertNotNull(blockStore.getBlockByHash(genesisBlock.blockHash));
        assertNotNull(blockStore.getBlockByHash(block1.blockHash));
        assertNotNull(blockStore.getBlockByHash(block2.blockHash));
        assertNotNull(blockStore.getBlockByHash(block3.blockHash));
        assertNull(blockStore.getBlockByHash(block4.blockHash));
        assertNull(blockStore.getBlockByHash(block5.blockHash));
    }
}

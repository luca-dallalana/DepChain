package blockchain;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class GenesisBlockTest {

    @Test
    public void testGenesisCreation() {
        System.out.println("=== Genesis Block Creation Test ===\n");

        String projectRoot = "/Users/lucagrespandallalana/Documents/IST/SD/SEC/Project/SEC_project";

        // Clean up existing genesis file if it exists
        String genesisPath = projectRoot + "/blockchain_data/genesis_block.json";
        try {
            Files.deleteIfExists(Paths.get(genesisPath));
        } catch (Exception e) {
            // Ignore
        }

        // Create genesis block
        Block genesis = Block.createAndSaveGenesis(projectRoot);

        // Create BlockStore with genesis block
        BlockStore blockStore = new BlockStore(genesis);

        // Verify genesis block properties
        assertNotNull(genesis, "Genesis block should not be null");
        assertNotNull(genesis.blockHash, "Genesis block hash should not be null");
        assertEquals(0, genesis.blockNumber, "Genesis block number should be 0");
        assertNull(genesis.parentBlockHash, "Genesis parent hash should be null");
        assertTrue(genesis.isGenesisBlock(), "Should be identified as genesis block");

        // Verify transactions
        assertEquals(2, genesis.transactions.size(), "Genesis should have 2 deployment transactions");

        // Verify state
        assertEquals(4, genesis.state.accounts.size(), "Genesis state should have 4 accounts");

        // Verify contract addresses exist
        assertTrue(genesis.state.hasAccount(Block.ACCESS_CONTROL_ADDRESS),
            "AccessControl contract should exist");
        assertTrue(genesis.state.hasAccount(Block.IST_COIN_ADDRESS),
            "ISTCoin contract should exist");

        // Verify contracts are identified correctly
        blockchain.Account acAccount = genesis.state.getAccount(Block.ACCESS_CONTROL_ADDRESS);
        assertNotNull(acAccount, "AccessControl account should exist");
        assertTrue(acAccount.isContract(), "AccessControl should be a contract");
        assertNotNull(acAccount.getCode(), "AccessControl should have code");

        blockchain.Account istAccount = genesis.state.getAccount(Block.IST_COIN_ADDRESS);
        assertNotNull(istAccount, "ISTCoin account should exist");
        assertTrue(istAccount.isContract(), "ISTCoin should be a contract");
        assertNotNull(istAccount.getCode(), "ISTCoin should have code");

        // Verify file was created
        File genesisFile = new File(genesisPath);
        assertTrue(genesisFile.exists(), "Genesis block file should exist");

        // Verify block was stored in BlockStore
        Block retrievedBlock = blockStore.getBlockByHash(genesis.blockHash);
        assertNotNull(retrievedBlock, "Genesis block should be in BlockStore");
        assertEquals(genesis.blockHash, retrievedBlock.blockHash, "Retrieved block should match genesis");

        System.out.println("\n=== Genesis Block Test Passed ===");
        System.out.println("Genesis block hash: " + genesis.blockHash);
        System.out.println("Accounts in genesis state: " + genesis.state.accounts.size());
        System.out.println("File saved at: " + genesisPath);
        System.out.println("Block stored in BlockStore: " + (retrievedBlock != null));
    }
}

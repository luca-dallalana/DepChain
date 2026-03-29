package blockchain;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.hyperledger.besu.datatypes.Address;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

public class GenesisBlockTest {

    @Test
    public void testGenesisCreation() {
        String projectRoot = "..";

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
        assertEquals(1, genesis.transactions.size(), "Genesis should have 1 deployment transaction");

        // Verify state (admin + 2 clients + 1 contract)
        assertEquals(4, genesis.state.accounts.size(), "Genesis state should have 4 accounts");

        // Verify admin account exists (used for deploying contracts in genesis)
        Address adminAddress = Address.fromHexString(Block.ADMIN_ADDRESS);
        assertTrue(genesis.state.hasAccount(adminAddress), "Admin account should exist");
        blockchain.Account adminAccount = genesis.state.getAccount(adminAddress);
        assertNotNull(adminAccount, "Admin account should not be null");
        assertTrue(adminAccount.balance > 0, "Admin should have positive DepCoin balance");

        // Verify ISTCoin contract address exists
        Address istCoinAddress = Address.fromHexString(Block.IST_COIN_ADDRESS);
        assertTrue(genesis.state.hasAccount(istCoinAddress),
            "ISTCoin contract should exist");

        // Verify ISTCoin contract is identified correctly
        blockchain.Account istAccount = genesis.state.getAccount(istCoinAddress);
        assertNotNull(istAccount, "ISTCoin account should exist");
        assertTrue(istAccount.isContract(), "ISTCoin should be a contract");
        assertNotNull(istAccount.getCode(), "ISTCoin should have code");

        // Verify client accounts have nonce=0 (admin deployed contracts, not clients)
        Address client0Address = Address.fromHexString(
            blockchain.AddressUtils.generateAddressFromPublicKey(projectRoot + "/rsa_keys/client_0/client_0.pubkey")
        );
        Address client1Address = Address.fromHexString(
            blockchain.AddressUtils.generateAddressFromPublicKey(projectRoot + "/rsa_keys/client_1/client_1.pubkey")
        );
        blockchain.Account client0Account = genesis.state.getAccount(client0Address);
        blockchain.Account client1Account = genesis.state.getAccount(client1Address);
        assertNotNull(client0Account, "Client0 account should exist");
        assertNotNull(client1Account, "Client1 account should exist");
        assertEquals(0, client0Account.nonce_count, "Client0 should have nonce=0");
        assertEquals(0, client1Account.nonce_count, "Client1 should have nonce=0");
        assertTrue(client0Account.balance > 0, "Client0 should have positive DepCoin balance");
        assertTrue(client1Account.balance > 0, "Client1 should have positive DepCoin balance");

        // Verify file was created
        File genesisFile = new File(genesisPath);
        assertTrue(genesisFile.exists(), "Genesis block file should exist");

        // Verify block was stored in BlockStore
        Block retrievedBlock = blockStore.getBlockByHash(genesis.blockHash);
        assertNotNull(retrievedBlock, "Genesis block should be in BlockStore");
        assertEquals(genesis.blockHash, retrievedBlock.blockHash, "Retrieved block should match genesis");
    }
}

package blockchain;

import blockchain.evm.EVMHelper;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ReplayAttackTest {

    private WorldState genesisState;
    private Address client0;
    private Address client1;
    private Address client2;

    @BeforeEach
    public void setup() {
        String projectRoot = "..";

        // Generate client addresses
        String client0Addr = AddressUtils.generateAddressFromPublicKey(projectRoot + "/rsa_keys/client_0/client_0.pubkey");
        String client1Addr = AddressUtils.generateAddressFromPublicKey(projectRoot + "/rsa_keys/client_1/client_1.pubkey");
        String client2Addr = AddressUtils.generateAddressFromPublicKey(projectRoot + "/rsa_keys/client_2/client_2.pubkey");
        client0 = Address.fromHexString(client0Addr);
        client1 = Address.fromHexString(client1Addr);
        client2 = Address.fromHexString(client2Addr);

        // Load genesis state
        Block genesis = Block.createAndSaveGenesis(projectRoot);
        genesisState = genesis.state;

    }

    @Test
    public void testSameNonceReplayBlocked() {

        List<Transaction> transactions = new ArrayList<>();

        Transaction tx1 = new Transaction(
            4000,
            client0,
            client1,
            1000L,
            new byte[0],
            21000L,
            1L,
            0,  // nonce = 0
            null
        );
        transactions.add(tx1);

        WorldState afterBlock1 = BlockchainMember.computeState(new EVMHelper(), transactions, genesisState);

        assertTrue(tx1.getExecutionSuccess(), "Original transaction should succeed");
        assertEquals(1, afterBlock1.getAccount(client0).nonce_count, "Client0 nonce should be 1");

        long client0BalanceAfter = afterBlock1.getAccount(client0).balance;
        long client1BalanceAfter = afterBlock1.getAccount(client1).balance;

        Transaction replayTx = new Transaction(
            4000,
            client0,
            client1,
            1000L,  // Same amount
            new byte[0],
            21000L,
            1L,
            0,  // SAME nonce = 0 (REPLAY ATTACK)
            null
        );

        List<Transaction> replayBlock = new ArrayList<>();
        replayBlock.add(replayTx);

        WorldState afterReplay = BlockchainMember.computeState(new EVMHelper(), replayBlock, afterBlock1);

        assertFalse(replayTx.getExecutionSuccess(), "Replay transaction should FAIL");

        assertEquals(client0BalanceAfter, afterReplay.getAccount(client0).balance,
                    "Client0 balance should be unchanged");
        assertEquals(client1BalanceAfter, afterReplay.getAccount(client1).balance,
                    "Client1 balance should be unchanged");
    }

    @Test
    public void testOldTransactionReplayBlocked() {

        WorldState currentState = genesisState;

        for (int i = 0; i < 5; i++) {
            List<Transaction> txList = new ArrayList<>();
            Transaction tx = new Transaction(
                4000,
                client0,
                client1,
                100L,  // Send 100 each time
                new byte[0],
                21000L,
                1L,
                i,  // nonce increments
                null
            );
            txList.add(tx);
            currentState = BlockchainMember.computeState(new EVMHelper(), txList, currentState);
            assertTrue(tx.getExecutionSuccess(), "Transaction " + i + " should succeed");
        }

        assertEquals(5, currentState.getAccount(client0).nonce_count, "Client0 nonce should be 5");

        // REPLAY ATTACK: Try to replay transaction from 3 blocks ago (nonce=2)
        Transaction replayOld = new Transaction(
            4000,
            client0,
            client1,
            100L,
            new byte[0],
            21000L,
            1L,
            2,  // Old nonce from 3 blocks ago (REPLAY ATTACK)
            null
        );

        List<Transaction> replayBlock = new ArrayList<>();
        replayBlock.add(replayOld);

        WorldState afterReplay = BlockchainMember.computeState(new EVMHelper(), replayBlock, currentState);

        // Should fail because nonce 2 < lastExecutedNonce 4
        assertFalse(replayOld.getExecutionSuccess(), "Old replay should FAIL");

    }

    @Test
    public void testSequentialNonceEnforcement() {

        // Try to submit transaction with nonce=5 (skipping 0-4)
        Transaction skipNonceTx = new Transaction(
            4000,
            client0,
            client1,
            1000L,
            new byte[0],
            21000L,
            1L,
            5,  // Skipped nonces 0-4 (INVALID)
            null
        );

        List<Transaction> txList = new ArrayList<>();
        txList.add(skipNonceTx);

        WorldState afterExecution = BlockchainMember.computeState(new EVMHelper(), txList, genesisState);

        assertFalse(skipNonceTx.getExecutionSuccess(), "Skipping nonces should FAIL");
        assertEquals(0, afterExecution.getAccount(client0).nonce_count,
                    "Account nonce should remain 0 (unchanged)");

        // Now submit correct nonce=0
        Transaction correctTx = new Transaction(
            4000,
            client0,
            client1,
            1000L,
            new byte[0],
            21000L,
            1L,
            0,  // Correct nonce
            null
        );

        List<Transaction> correctBlock = new ArrayList<>();
        correctBlock.add(correctTx);

        WorldState afterCorrect = BlockchainMember.computeState(new EVMHelper(), correctBlock, genesisState);

        assertTrue(correctTx.getExecutionSuccess(), "Correct nonce should succeed");
        assertEquals(1, afterCorrect.getAccount(client0).nonce_count, "Nonce should increment to 1");

    }
}

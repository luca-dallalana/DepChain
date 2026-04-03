package blockchain;

import blockchain.evm.EVMHelper;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class InvalidGasTest {

    private WorldState genesisState;
    private Address client0;
    private Address client1;
    private Address client2;

    @BeforeEach
    public void setup() {
        String projectRoot = "..";

        String client0Addr = AddressUtils.generateAddressFromPublicKey(projectRoot + "/rsa_keys/client_0/client_0.pubkey");
        String client1Addr = AddressUtils.generateAddressFromPublicKey(projectRoot + "/rsa_keys/client_1/client_1.pubkey");
        String client2Addr = AddressUtils.generateAddressFromPublicKey(projectRoot + "/rsa_keys/client_2/client_2.pubkey");

        client0 = Address.fromHexString(client0Addr);
        client1 = Address.fromHexString(client1Addr);
        client2 = Address.fromHexString(client2Addr);

        Block genesis = Block.createAndSaveGenesis(projectRoot);
        genesisState = genesis.state;

    }

    @Test
    public void testGasLimitExceeded() {

        List<Transaction> transactions = new ArrayList<>();

        Transaction tx = new Transaction(
            4000,
            client0,
            client1,
            1000L,
            new byte[0],
            100L,  // Insufficient gas limit (needs 21,000)
            1L,
            0,
            null
        );
        transactions.add(tx);

        // Execute transaction
        WorldState finalState = BlockchainMember.computeState(new EVMHelper(), transactions, genesisState);

        long client0InitialBalance = genesisState.getAccount(client0).balance;
        long client0FinalBalance = finalState.getAccount(client0).balance;

        // Verify balance was deducted for gas
        assertTrue(client0FinalBalance < client0InitialBalance,
                  "Balance should decrease (gas consumed)");
        // Verify transaction failed
        assertFalse(tx.getExecutionSuccess(), "Transaction should FAIL (gas limit too low)");
    }

    @Test
    public void testGasParametersInBlockOrdering() {

        List<Transaction> transactions = new ArrayList<>();

        // Create 3 transactions with different gas prices from DIFFERENT senders
        // NOTE: Same sender's transactions must maintain nonce order, so we need different senders
        Transaction tx1 = new Transaction(4000, client0, client1, 100L, new byte[0], 21000L, 1L, 0, null);
        Transaction tx2 = new Transaction(4001, client1, client0, 100L, new byte[0], 21000L, 5L, 0, null);
        Transaction tx3 = new Transaction(4002, client2, client0, 100L, new byte[0], 21000L, 3L, 0, null);

        transactions.add(tx1);
        transactions.add(tx2);
        transactions.add(tx3);


        // Order transactions by gas price (simulates block building)
        List<Transaction> orderedTxs = BlockchainMember.orderTransactionsForBlock(new ArrayList<>(transactions));

        // Verify ordering (highest gas price first)
        assertTrue(orderedTxs.get(0).getGasPrice() >= orderedTxs.get(1).getGasPrice(),
                  "First tx should have higher or equal gas price than second");
        assertTrue(orderedTxs.get(1).getGasPrice() >= orderedTxs.get(2).getGasPrice(),
                  "Second tx should have higher or equal gas price than third");

    }
}

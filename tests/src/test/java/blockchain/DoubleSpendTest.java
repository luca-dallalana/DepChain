package blockchain;

import blockchain.evm.EVMHelper;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DoubleSpendTest {

    private WorldState genesisState;
    private Address client0;
    private Address client1;
    private Address client2;
    private long client0InitialBalance;

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
        client0InitialBalance = genesisState.getAccount(client0).balance;

    }

    @Test
    public void testSameBlockDoubleSpend() {

        List<Transaction> transactions = new ArrayList<>();

        // Client0 has limited balance, tries to spend it twice
        long amountToSend = (long) (client0InitialBalance * 0.8); // 80% of balance

        // Transaction 1: Send 80% to Client1 (nonce=0, higher gas price)
        Transaction tx1 = new Transaction(
            4000,
            client0,
            client1,
            amountToSend,
            new byte[0],
            21000L,
            2L,
            0L,
            0,
            null
        );
        transactions.add(tx1);

        // Transaction 2: Send another 80% to Client2 (nonce=1, lower gas price)
        Transaction tx2 = new Transaction(
            4000,
            client0,
            client2,
            amountToSend,
            new byte[0],
            21000L,
            1L,
            0L,
            1,
            null
        );
        transactions.add(tx2);

        WorldState finalState = BlockchainMember.computeState(new EVMHelper(), transactions, genesisState, 1L);

        // First transaction should succeed (highest gas price)
        assertTrue(tx1.getExecutionSuccess(), "First transaction (higher gas price) should SUCCEED");

        // Second transaction should FAIL (insufficient balance after first tx)
        assertFalse(tx2.getExecutionSuccess(), "Second transaction should FAIL (double-spend attempt)");

        // Verify Client0 balance
        if (finalState.hasAccount(client0)) {
            Account client0Final = finalState.getAccount(client0);
            long expectedBalance = client0InitialBalance - amountToSend - 21000;
            assertEquals(expectedBalance, client0Final.balance, "Client0 balance should reflect only the first transaction and gas costs");
        }

        // Verify Client1 received funds, Client2 did not
        if (finalState.hasAccount(client1)) {
            long client1InitialBalance = genesisState.getAccount(client1).balance;
            long client1Balance = finalState.getAccount(client1).balance;
            assertEquals(client1InitialBalance + amountToSend, client1Balance, "Client1 should receive the amount sent in tx1");
        }

        if (finalState.hasAccount(client2)) {
            long client2Balance = finalState.getAccount(client2).balance;
            assertEquals(0, client2Balance, "Client2 should have 0 (tx2 failed)");
        }
    }
}

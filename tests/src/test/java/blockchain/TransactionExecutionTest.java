package blockchain;

import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import blockchain.evm.ABIEncoder;
import blockchain.evm.EVMHelper;

public class TransactionExecutionTest {

    private WorldState genesisState;
    private Address client0;
    private Address client1;
    private Address newAccount;
    private Address istCoinAddress;

    @BeforeEach
    public void setup() {
        String projectRoot = "..";

        // Generate client addresses
        String client0Addr = AddressUtils.generateAddressFromPublicKey(projectRoot + "/rsa_keys/client_0/client_0.pubkey");
        String client1Addr = AddressUtils.generateAddressFromPublicKey(projectRoot + "/rsa_keys/client_1/client_1.pubkey");

        client0 = Address.fromHexString(client0Addr);
        client1 = Address.fromHexString(client1Addr);
        newAccount = Address.fromHexString("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        istCoinAddress = Address.fromHexString(Block.IST_COIN_ADDRESS);

        // Load genesis state
        Block genesis = Block.createAndSaveGenesis(projectRoot);
        genesisState = genesis.state;

        System.out.println("\n=== Test Setup Complete ===");
        System.out.println("Client0: " + client0.toHexString());
        System.out.println("Client0 initial balance: " + genesisState.getAccount(client0).balance + " DepCoin");
        System.out.println("Client1: " + client1.toHexString());
        System.out.println("Client1 initial balance: " + genesisState.getAccount(client1).balance + " DepCoin\n");
    }

    @Test
    public void testNativeDepCoinTransfer() {
        System.out.println("=== Test: Native DepCoin Transfer ===\n");

        EVMHelper evm = new EVMHelper();
        List<Transaction> transactions = new ArrayList<>();

        // Client0 sends 1000 DepCoin to Client1
        long transferAmount = 1000;
        long gasPrice = 1;
        long gasLimit = 21000;

        Transaction tx = new Transaction(
            -1,
            client0,
            client1,
            transferAmount,
            new byte[0],
            gasLimit,
            gasPrice,
            0,
            0,
            null
        );
        transactions.add(tx);

        WorldState finalState = BlockchainMember.computeState(evm, transactions, genesisState, 1L);

        // Verify balances
        Account client0Final = finalState.getAccount(client0);
        Account client1Final = finalState.getAccount(client1);

        long client0InitialBalance = genesisState.getAccount(client0).balance;
        long client1InitialBalance = genesisState.getAccount(client1).balance;

        long gasFee = gasPrice * 21000;  // Native transfer uses 21,000 gas

        assertEquals(client0InitialBalance - transferAmount - gasFee, client0Final.balance,
            "Client0 balance should decrease by transfer amount + gas fee");
        assertEquals(client1InitialBalance + transferAmount, client1Final.balance,
            "Client1 balance should increase by transfer amount");

        // Verify nonce incremented
        assertEquals(1, client0Final.nonce_count, "Client0 nonce should be 1 after transaction");

        System.out.println("Client0 final balance: " + client0Final.balance + " DepCoin (sent " + transferAmount + " + " + gasFee + " gas)");
        System.out.println("Client1 final balance: " + client1Final.balance + " DepCoin (received " + transferAmount + ")");
        System.out.println("Test PASSED\n");
    }

    @Test
    public void testInvalidNonce() {
        System.out.println("=== Test: Invalid Nonce Rejection ===\n");

        EVMHelper evm = new EVMHelper();
        List<Transaction> transactions = new ArrayList<>();

        // Create transaction with wrong nonce (should be 0, but using 5)
        Transaction tx = new Transaction(
            -1,
            client0,
            client1,
            1000,
            new byte[0],
            21000,
            1,
            0,
            5,
            null
        );
        transactions.add(tx);

        WorldState resultState = BlockchainMember.computeState(evm, transactions, genesisState, 1L);

        // Verify transaction was marked as failed
        assertNotNull(resultState, "State should be returned even with invalid transaction");
        assertFalse(tx.getExecutionSuccess(), "Transaction with invalid nonce should have executionSuccess = false");

        System.out.println("Transaction marked as failed with executionSuccess = false");
        System.out.println("Test PASSED\n");
    }

    @Test
    public void testInsufficientBalance() {
        System.out.println("=== Test: Insufficient Balance Rejection ===\n");

        EVMHelper evm = new EVMHelper();
        List<Transaction> transactions = new ArrayList<>();

        long client0Balance = genesisState.getAccount(client0).balance;

        // Try to send more than available (including gas)
        Transaction tx = new Transaction(
            -1,
            client0,
            client1,
            client0Balance,
            new byte[0],
            21000,
            1,
            0,
            0,
            null
        );
        transactions.add(tx);

        WorldState resultState = BlockchainMember.computeState(evm, transactions, genesisState, 1L);

        // Verify transaction was marked as failed
        assertNotNull(resultState, "State should be returned even with invalid transaction");
        assertFalse(tx.getExecutionSuccess(), "Transaction with insufficient balance should have executionSuccess = false");

        System.out.println("Transaction marked as failed with executionSuccess = false");
        System.out.println("Test PASSED\n");
    }

    @Test
    public void testContractCallWithGas() {
        System.out.println("=== Test: Contract Call with Gas Deduction ===\n");

        EVMHelper evm = new EVMHelper();
        List<Transaction> transactions = new ArrayList<>();

        // Call ISTCoin.balanceOf(admin) to verify contract call works
        Address admin = Address.fromHexString(Block.ADMIN_ADDRESS);
        Bytes callData = ABIEncoder.encodeBalanceOf(admin);

        long gasPrice = 1;
        long gasLimit = 100000;  // Reduced to fit within client0's balance

        Transaction tx = new Transaction(
            -1,
            client0,
            istCoinAddress,
            0,
            callData.toArray(),
            gasLimit,
            gasPrice,
            0,
            0,
            null
        );
        transactions.add(tx);

        long client0InitialBalance = genesisState.getAccount(client0).balance;

        WorldState finalState = BlockchainMember.computeState(evm, transactions, genesisState, 1L);

        // Verify gas was deducted based on actual gas used (not necessarily full gas limit)
        Account client0Final = finalState.getAccount(client0);
        long actualGasFee = client0InitialBalance - client0Final.balance;
        long maxPossibleGasFee = gasPrice * gasLimit;

        assertTrue(actualGasFee > 0,
            "Client0 balance should decrease by a positive gas fee");
        assertTrue(actualGasFee <= maxPossibleGasFee,
            "Gas fee should not exceed gasPrice * gasLimit");

        // Verify nonce incremented
        assertEquals(1, client0Final.nonce_count, "Client0 nonce should be 1 after contract call");

        System.out.println("Client0 balance after contract call: " + client0Final.balance + " DepCoin");
        System.out.println("Gas fee paid: " + actualGasFee + " DepCoin");
        System.out.println("Test PASSED\n");
    }

    @Test
    public void testMultipleTransactionsWithNonceSequence() {
        System.out.println("=== Test: Multiple Transactions with Nonce Sequence ===\n");

        EVMHelper evm = new EVMHelper();
        List<Transaction> transactions = new ArrayList<>();

        // Client0 sends 3 transactions with nonces 0, 1, 2
        for (int i = 0; i < 3; i++) {
            Transaction tx = new Transaction(
                -1,
                client0,
                client1,
                100,
                new byte[0],
                21000,
                1,
                0,
                i,
                null
            );
            transactions.add(tx);
        }

        long client0InitialBalance = genesisState.getAccount(client0).balance;
        long client1InitialBalance = genesisState.getAccount(client1).balance;

        // Execute all transactions
        WorldState finalState = BlockchainMember.computeState(evm, transactions, genesisState, 1L);

        // Verify results
        Account client0Final = finalState.getAccount(client0);
        Account client1Final = finalState.getAccount(client1);

        long totalTransferred = 300;  // 100 * 3
        long totalGasFees = 21000 * 3 * 1;  // 21000 gas * 3 txs * 1 gasPrice

        assertEquals(client0InitialBalance - totalTransferred - totalGasFees, client0Final.balance,
            "Client0 should have sent 300 DepCoin + gas fees");
        assertEquals(client1InitialBalance + totalTransferred, client1Final.balance,
            "Client1 should have received 300 DepCoin");
        assertEquals(3, client0Final.nonce_count, "Client0 nonce should be 3 after 3 transactions");

        System.out.println("Client0 executed 3 transactions successfully");
        System.out.println("Client0 final nonce: " + client0Final.nonce_count);
        System.out.println("Total transferred: " + totalTransferred + " DepCoin");
        System.out.println("Total gas fees: " + totalGasFees + " DepCoin");
        System.out.println("Test PASSED\n");
    }
}

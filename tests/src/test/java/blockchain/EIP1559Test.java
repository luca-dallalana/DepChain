package blockchain;

import blockchain.evm.EVMHelper;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EIP1559Test {

    private static final long BLOCK_GAS_TARGET = 105000L;
    private static final long BLOCK_GAS_LIMIT = 210000L;

    private WorldState genesisState;
    private Block genesisBlock;
    private Address client0;
    private Address client1;

    @BeforeEach
    public void setup() {
        String projectRoot = "..";

        String client0Addr = AddressUtils.generateAddressFromPublicKey(projectRoot + "/rsa_keys/client_0/client_0.pubkey");
        String client1Addr = AddressUtils.generateAddressFromPublicKey(projectRoot + "/rsa_keys/client_1/client_1.pubkey");

        client0 = Address.fromHexString(client0Addr);
        client1 = Address.fromHexString(client1Addr);

        genesisBlock = Block.createAndSaveGenesis(projectRoot);
        genesisState = genesisBlock.state;
    }

    @Test
    public void testBaseFeeIncreasesWhenFull() {
        long parentBaseFee = 100L;
        long parentGasUsed = BLOCK_GAS_LIMIT;

        long newFee = BlockchainMember.computeBaseFee(parentBaseFee, parentGasUsed);

        assertTrue(newFee > parentBaseFee, "Base fee should increase when block is above target");
    }

    @Test
    public void testBaseFeeDecreasesWhenEmpty() {
        long parentBaseFee = 100L;
        long parentGasUsed = 0L;

        long newFee = BlockchainMember.computeBaseFee(parentBaseFee, parentGasUsed);

        assertTrue(newFee < parentBaseFee, "Base fee should decrease when block is below target");
    }

    @Test
    public void testBaseFeeUnchangedAtTarget() {
        long parentBaseFee = 100L;
        long parentGasUsed = BLOCK_GAS_TARGET;

        long newFee = BlockchainMember.computeBaseFee(parentBaseFee, parentGasUsed);

        assertEquals(parentBaseFee, newFee, "Base fee should not change when gas used equals target");
    }

    @Test
    public void testBaseFeeNeverBelowOne() {
        long newFee = BlockchainMember.computeBaseFee(1L, 0L);

        assertTrue(newFee >= 1L, "Base fee floor is 1");
        assertEquals(1L, newFee);
    }

    @Test
    public void testTxRejectedBelowBaseFee() {
        long baseFee = 10L;
        Transaction tx = new Transaction(4000, client0, client1, 100L, new byte[0], 21000L, 5L, 0L, 0, null);

        BlockchainMember.computeState(new EVMHelper(), List.of(tx), genesisState, baseFee);

        assertFalse(tx.getExecutionSuccess(), "Transaction with maxFeePerGas below baseFee must be rejected");
    }

    @Test
    public void testEffectiveGasPriceCapped() {
        long baseFee = 1L;
        long maxFee = 3L;
        long tip = 10L;
        // effectiveGasPrice = min(3, 1+10) = 3

        long initialBalance = genesisState.getAccount(client0).balance;

        Transaction tx = new Transaction(4000, client0, client1, 0L, new byte[0], 21000L, maxFee, tip, 0, null);
        WorldState finalState = BlockchainMember.computeState(new EVMHelper(), List.of(tx), genesisState, baseFee);

        long gasCost = initialBalance - finalState.getAccount(client0).balance;
        assertEquals(maxFee * tx.gasUsed, gasCost, "Sender charged at maxFeePerGas, not baseFee + tip");
    }

    @Test
    public void testTipDoesNotExceedCap() {
        long baseFee = 5L;
        long maxFee = 7L;
        long tip = 10L;
        // effectiveGasPrice = min(7, 5+10) = 7

        long initialBalance = genesisState.getAccount(client0).balance;

        Transaction tx = new Transaction(4000, client0, client1, 0L, new byte[0], 21000L, maxFee, tip, 0, null);
        WorldState finalState = BlockchainMember.computeState(new EVMHelper(), List.of(tx), genesisState, baseFee);

        long gasCost = initialBalance - finalState.getAccount(client0).balance;
        assertEquals(maxFee * tx.gasUsed, gasCost, "Effective gas price must not exceed maxFeePerGas");
    }

    @Test
    public void testGasFeeBurned() {
        long baseFee = 1L;

        long totalSupplyBefore = genesisState.accounts.values().stream()
            .mapToLong(a -> a.balance)
            .sum();

        Transaction tx = new Transaction(4000, client0, client1, 1000L, new byte[0], 21000L, 1L, 0L, 0, null);
        WorldState finalState = BlockchainMember.computeState(new EVMHelper(), List.of(tx), genesisState, baseFee);

        long totalSupplyAfter = finalState.accounts.values().stream()
            .mapToLong(a -> a.balance)
            .sum();

        long burnedFee = 1L * tx.gasUsed;
        assertEquals(totalSupplyBefore - burnedFee, totalSupplyAfter, "Gas fee must be burned, not credited to any account");
    }

    @Test
    public void testOrderByTip() {
        long baseFee = 1L;

        Transaction lowTip = new Transaction(4000, client0, client1, 0L, new byte[0], 21000L, 10L, 1L, 0, null);
        Transaction highTip = new Transaction(4001, client1, client0, 0L, new byte[0], 21000L, 10L, 5L, 0, null);

        List<Transaction> txs = new ArrayList<>();
        txs.add(lowTip);
        txs.add(highTip);

        List<Transaction> ordered = BlockchainMember.orderTransactionsForBlock(txs, baseFee);

        assertEquals(highTip, ordered.get(0), "Transaction with higher tip should come first");
        assertEquals(lowTip, ordered.get(1), "Transaction with lower tip should come second");
    }

    @Test
    public void testTotalGasUsedTracked() throws Exception {
        Transaction tx = new Transaction(4000, client0, client1, 1000L, new byte[0], 21000L, 1L, 0L, 0, null);

        Block block = BlockchainMember.buildBlock(genesisBlock, List.of(tx));

        long expectedGasUsed = block.transactions.stream().mapToLong(t -> t.gasUsed).sum();
        assertEquals(expectedGasUsed, block.totalGasUsed, "block.totalGasUsed must match sum of tx.gasUsed");
        assertTrue(block.totalGasUsed > 0, "totalGasUsed must be positive after executing transactions");
    }
}

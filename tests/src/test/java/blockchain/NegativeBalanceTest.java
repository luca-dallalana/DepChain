package blockchain;

import blockchain.evm.ABIEncoder;
import blockchain.evm.EVMHelper;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class NegativeBalanceTest {

    private WorldState genesisState;
    private Address client0;
    private Address client1;
    private Address istCoinAddress;
    private long client0InitialBalance;

    @BeforeEach
    public void setup() {
        String projectRoot = "..";

        String client0Addr = AddressUtils.generateAddressFromPublicKey(projectRoot + "/rsa_keys/client_0/client_0.pubkey");
        String client1Addr = AddressUtils.generateAddressFromPublicKey(projectRoot + "/rsa_keys/client_1/client_1.pubkey");

        client0 = Address.fromHexString(client0Addr);
        client1 = Address.fromHexString(client1Addr);
        istCoinAddress = Address.fromHexString(Block.IST_COIN_ADDRESS);

        Block genesis = Block.createAndSaveGenesis(projectRoot);
        genesisState = genesis.state;
        client0InitialBalance = genesisState.getAccount(client0).balance;

    }

    @Test
    public void testContractCallCausesNegativeBalance() {

        List<Transaction> transactions = new ArrayList<>();

        // Set low balance for this test
        long lowBalance = 50000L;
        genesisState.getAccount(client0).balance = lowBalance;

        // Try to call ISTCoin.transfer with excessive gas limit
        long excessiveGasLimit = 100000L;
        long gasPrice = 1L;
        long totalGasCost = excessiveGasLimit * gasPrice;

        Bytes callData = ABIEncoder.encodeTransfer(client1, BigInteger.valueOf(100));

        Transaction tx = new Transaction(
            4000,
            client0,
            istCoinAddress,
            0L,
            callData.toArray(),
            excessiveGasLimit,
            gasPrice,
            0L,
            0,
            null
        );
        transactions.add(tx);

        WorldState finalState = BlockchainMember.computeState(new EVMHelper(), transactions, genesisState, 1L);

        if (totalGasCost > lowBalance) {
            // Should fail if gas cost exceeds balance
            assertFalse(tx.getExecutionSuccess(),
                       "Transaction should FAIL (gas cost > balance)");
        } else {
            // Should succeed if gas cost is within balance
            assertTrue(tx.getExecutionSuccess(),
                      "Transaction should succeed (sufficient balance)");
        }

        // Verify balance is non-negative
        long finalBalance = finalState.getAccount(client0).balance;
        assertTrue(finalBalance >= 0, "Balance must be non-negative");

    }

}

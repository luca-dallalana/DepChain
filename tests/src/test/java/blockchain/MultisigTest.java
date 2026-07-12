package blockchain;

import blockchain.evm.ABIEncoder;
import blockchain.evm.EVMHelper;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class MultisigTest {

    private static final String IST_COIN_BYTECODE  = BytecodeLoader.loadBytecode("ISTCoin");
    private static final String MULTISIG_BYTECODE  = BytecodeLoader.loadBytecode("Multisig");

    private static final Address DEPLOYER   = Address.fromHexString("0x1111111111111111111111111111111111111111");
    private static final Address OWNER1     = Address.fromHexString("0x1111111111111111111111111111111111111112");
    private static final Address OWNER2     = Address.fromHexString("0x1111111111111111111111111111111111111113");
    private static final Address RECIPIENT  = Address.fromHexString("0x1111111111111111111111111111111111111114");
    private static final Address OUTSIDER   = Address.fromHexString("0x1111111111111111111111111111111111111115");

    private static final Address IST_COIN_ADDRESS = Address.fromHexString("0x5555555555555555555555555555555555555555");
    private static final Address MULTISIG_ADDRESS = Address.fromHexString("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

    private static final BigInteger THRESHOLD  = BigInteger.TWO;
    private static final BigInteger FUND_AMOUNT = BigInteger.valueOf(10_000L);
    private static final BigInteger TX_AMOUNT   = BigInteger.valueOf(1_000L);

    private EVMHelper evm;

    @BeforeEach
    public void setup() {
        evm = new EVMHelper();

        evm.createAccount(DEPLOYER,  Wei.fromEth(1000));
        evm.createAccount(OWNER1,    Wei.fromEth(1000));
        evm.createAccount(OWNER2,    Wei.fromEth(1000));
        evm.createAccount(RECIPIENT, Wei.fromEth(1000));
        evm.createAccount(OUTSIDER,  Wei.fromEth(1000));

        // Deploy ISTCoin — OWNER1 and OWNER2 each get half supply
        Bytes istCode = Bytes.concatenate(
            Bytes.fromHexString(IST_COIN_BYTECODE),
            ABIEncoder.encodeISTCoinConstructor(OWNER1, OWNER2)
        );
        assertTrue(evm.deployContract(DEPLOYER, IST_COIN_ADDRESS, istCode), "ISTCoin deploy failed");

        // Deploy Multisig with OWNER1 and OWNER2, threshold 2
        Bytes multisigCode = Bytes.concatenate(
            Bytes.fromHexString(MULTISIG_BYTECODE),
            ABIEncoder.encodeMultisigConstructor(Arrays.asList(OWNER1, OWNER2), THRESHOLD)
        );
        assertTrue(evm.deployContract(DEPLOYER, MULTISIG_ADDRESS, multisigCode), "Multisig deploy failed");

        // Fund the multisig with IST from OWNER1
        EVMHelper.ExecutionResult fundResult = evm.executeCall(OWNER1, IST_COIN_ADDRESS,
            ABIEncoder.encodeTransfer(MULTISIG_ADDRESS, FUND_AMOUNT));
        assertTrue(fundResult.isSuccess(), "Funding multisig failed");
    }

    private BigInteger getISTBalance(Address account) {
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, ABIEncoder.encodeBalanceOf(account));
        return evm.extractUint256FromReturnData();
    }

    private BigInteger submitTx() {
        EVMHelper.ExecutionResult r = evm.executeCall(OWNER1, MULTISIG_ADDRESS,
            ABIEncoder.encodeSubmit(IST_COIN_ADDRESS, RECIPIENT, TX_AMOUNT));
        assertTrue(r.isSuccess(), "submit failed");
        return evm.extractUint256FromReturnData();
    }

    @Test
    public void testInitialTransactionCountIsZero() {
        evm.executeCall(DEPLOYER, MULTISIG_ADDRESS, ABIEncoder.encodeGetTransactionCount());
        assertEquals(BigInteger.ZERO, evm.extractUint256FromReturnData());
    }

    @Test
    public void testSubmitTransaction() {
        BigInteger txId = submitTx();
        assertEquals(BigInteger.ZERO, txId, "first txId should be 0");

        evm.executeCall(DEPLOYER, MULTISIG_ADDRESS, ABIEncoder.encodeGetTransactionCount());
        assertEquals(BigInteger.ONE, evm.extractUint256FromReturnData(), "count should be 1 after submit");
    }

    @Test
    public void testConfirmTransaction() {
        BigInteger txId = submitTx();

        EVMHelper.ExecutionResult r = evm.executeCall(OWNER1, MULTISIG_ADDRESS,
            ABIEncoder.encodeConfirm(txId));
        assertTrue(r.isSuccess(), "confirm should succeed");

        evm.executeCall(DEPLOYER, MULTISIG_ADDRESS, ABIEncoder.encodeGetTransaction(txId));
        BigInteger confirmations = evm.extractUint256FromReturnData(4);
        assertEquals(BigInteger.ONE, confirmations, "confirmations should be 1");
    }

    @Test
    public void testExecuteRequiresThreshold() {
        BigInteger txId = submitTx();
        evm.executeCall(OWNER1, MULTISIG_ADDRESS, ABIEncoder.encodeConfirm(txId));

        // Only 1 of 2 confirmations — execute should revert
        EVMHelper.ExecutionResult r = evm.executeCall(OWNER1, MULTISIG_ADDRESS,
            ABIEncoder.encodeExecute(txId));
        assertFalse(r.isSuccess(), "execute should revert when threshold not met");
    }

    @Test
    public void testFullExecutionFlow() {
        BigInteger txId = submitTx();
        BigInteger recipientBalanceBefore = getISTBalance(RECIPIENT);

        evm.executeCall(OWNER1, MULTISIG_ADDRESS, ABIEncoder.encodeConfirm(txId));
        evm.executeCall(OWNER2, MULTISIG_ADDRESS, ABIEncoder.encodeConfirm(txId));

        EVMHelper.ExecutionResult r = evm.executeCall(OWNER1, MULTISIG_ADDRESS,
            ABIEncoder.encodeExecute(txId));
        assertTrue(r.isSuccess(), "execute should succeed after threshold met");

        assertEquals(recipientBalanceBefore.add(TX_AMOUNT), getISTBalance(RECIPIENT),
            "recipient should receive TX_AMOUNT");

        evm.executeCall(DEPLOYER, MULTISIG_ADDRESS, ABIEncoder.encodeGetTransaction(txId));
        BigInteger executed = evm.extractUint256FromReturnData(3);
        assertEquals(BigInteger.ONE, executed, "transaction should be marked executed");
    }

    @Test
    public void testNonOwnerCannotSubmit() {
        EVMHelper.ExecutionResult r = evm.executeCall(OUTSIDER, MULTISIG_ADDRESS,
            ABIEncoder.encodeSubmit(IST_COIN_ADDRESS, RECIPIENT, TX_AMOUNT));
        assertFalse(r.isSuccess(), "non-owner should be rejected on submit");
    }

    @Test
    public void testNonOwnerCannotConfirm() {
        BigInteger txId = submitTx();
        EVMHelper.ExecutionResult r = evm.executeCall(OUTSIDER, MULTISIG_ADDRESS,
            ABIEncoder.encodeConfirm(txId));
        assertFalse(r.isSuccess(), "non-owner should be rejected on confirm");
    }

    @Test
    public void testCannotConfirmTwice() {
        BigInteger txId = submitTx();
        evm.executeCall(OWNER1, MULTISIG_ADDRESS, ABIEncoder.encodeConfirm(txId));

        EVMHelper.ExecutionResult r = evm.executeCall(OWNER1, MULTISIG_ADDRESS,
            ABIEncoder.encodeConfirm(txId));
        assertFalse(r.isSuccess(), "double confirmation should revert");
    }

    @Test
    public void testRevokeConfirmation() {
        BigInteger txId = submitTx();
        evm.executeCall(OWNER1, MULTISIG_ADDRESS, ABIEncoder.encodeConfirm(txId));
        evm.executeCall(OWNER1, MULTISIG_ADDRESS, ABIEncoder.encodeRevoke(txId));

        evm.executeCall(DEPLOYER, MULTISIG_ADDRESS, ABIEncoder.encodeGetTransaction(txId));
        BigInteger confirmations = evm.extractUint256FromReturnData(4);
        assertEquals(BigInteger.ZERO, confirmations, "confirmations should be 0 after revoke");

        EVMHelper.ExecutionResult r = evm.executeCall(OWNER1, MULTISIG_ADDRESS,
            ABIEncoder.encodeExecute(txId));
        assertFalse(r.isSuccess(), "execute should revert after revoke drops below threshold");
    }

    @Test
    public void testCannotExecuteTwice() {
        BigInteger txId = submitTx();
        evm.executeCall(OWNER1, MULTISIG_ADDRESS, ABIEncoder.encodeConfirm(txId));
        evm.executeCall(OWNER2, MULTISIG_ADDRESS, ABIEncoder.encodeConfirm(txId));
        evm.executeCall(OWNER1, MULTISIG_ADDRESS, ABIEncoder.encodeExecute(txId));

        EVMHelper.ExecutionResult r = evm.executeCall(OWNER1, MULTISIG_ADDRESS,
            ABIEncoder.encodeExecute(txId));
        assertFalse(r.isSuccess(), "second execute should revert");
    }
}

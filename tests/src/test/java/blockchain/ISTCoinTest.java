package blockchain;

import blockchain.evm.ABIEncoder;
import blockchain.evm.EVMHelper;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ISTCoinTest {

    // Bytecode loaded from compiled .bin files at runtime
    private static final String IST_COIN_BYTECODE = BytecodeLoader.loadBytecode("ISTCoin");

    // Test addresses
    private static final Address DEPLOYER = Address.fromHexString("0x1111111111111111111111111111111111111111");
    private static final Address ALICE = Address.fromHexString("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    private static final Address BOB = Address.fromHexString("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    private static final Address CHARLIE = Address.fromHexString("0xcccccccccccccccccccccccccccccccccccccccc");
    private static final Address IST_COIN_ADDRESS = Address.fromHexString("0x5555555555555555555555555555555555555555");

    // Expected values
    private static final BigInteger TOTAL_SUPPLY = new BigInteger("10000000000"); // 100M * 100 (decimals=2)
    private static final BigInteger HALF_SUPPLY = TOTAL_SUPPLY.divide(BigInteger.valueOf(2));

    private EVMHelper evm;

    @BeforeEach
    public void setup() {
        evm = new EVMHelper();

        // Create accounts
        evm.createAccount(DEPLOYER, Wei.fromEth(1000));
        evm.createAccount(ALICE, Wei.fromEth(1000));
        evm.createAccount(BOB, Wei.fromEth(1000));
        evm.createAccount(CHARLIE, Wei.fromEth(1000));

        // Deploy ISTCoin with Alice and Bob as initial holders (50/50 split)
        Bytes istCoinConstructorParams = ABIEncoder.encodeISTCoinConstructor(ALICE, BOB);
        Bytes istCoinDeploymentCode = Bytes.concatenate(
            Bytes.fromHexString(IST_COIN_BYTECODE),
            istCoinConstructorParams
        );
        boolean istCoinDeployed = evm.deployContract(DEPLOYER, IST_COIN_ADDRESS, istCoinDeploymentCode);
        assertTrue(istCoinDeployed, "ISTCoin deployment should succeed");
    }

    @Test
    public void testDeploymentAndInitialAllocation() {
        // Verify total supply
        Bytes callData = ABIEncoder.encodeTotalSupply();
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, callData);
        BigInteger totalSupply = evm.extractUint256FromReturnData();
        assertEquals(TOTAL_SUPPLY, totalSupply, "Total supply should match expected value");

        // Verify Alice's initial balance
        callData = ABIEncoder.encodeBalanceOf(ALICE);
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, callData);
        BigInteger aliceBalance = evm.extractUint256FromReturnData();
        assertEquals(HALF_SUPPLY, aliceBalance, "Alice should have half of total supply");

        // Verify Bob's initial balance
        callData = ABIEncoder.encodeBalanceOf(BOB);
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, callData);
        BigInteger bobBalance = evm.extractUint256FromReturnData();
        assertEquals(HALF_SUPPLY, bobBalance, "Bob should have half of total supply");
    }

    @Test
    public void testTransfer() {
        // Alice transfers 1000 tokens to Bob
        Bytes callData = ABIEncoder.encodeTransfer(BOB, BigInteger.valueOf(1000));
        EVMHelper.ExecutionResult result = evm.executeCall(ALICE, IST_COIN_ADDRESS, callData);
        assertTrue(result.isSuccess(), "Transfer should succeed");

        // Verify Bob's balance increased
        callData = ABIEncoder.encodeBalanceOf(BOB);
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, callData);
        BigInteger bobBalance = evm.extractUint256FromReturnData();
        assertEquals(HALF_SUPPLY.add(BigInteger.valueOf(1000)), bobBalance,
            "Bob's balance should increase by 1000");

        // Verify Alice's balance decreased
        callData = ABIEncoder.encodeBalanceOf(ALICE);
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, callData);
        BigInteger aliceBalance = evm.extractUint256FromReturnData();
        assertEquals(HALF_SUPPLY.subtract(BigInteger.valueOf(1000)), aliceBalance,
            "Alice's balance should decrease by 1000");
    }

    @Test
    public void testTransferInsufficientBalance() {
        // Alice tries to transfer more than she has
        BigInteger excessiveAmount = HALF_SUPPLY.add(BigInteger.ONE);
        Bytes callData = ABIEncoder.encodeTransfer(BOB, excessiveAmount);
        EVMHelper.ExecutionResult result = evm.executeCall(ALICE, IST_COIN_ADDRESS, callData);

        assertFalse(result.isSuccess(), "Transfer should fail with insufficient balance");
    }

    @Test
    public void testApproveAndTransferFrom() {
        // Alice approves Bob for 500 tokens
        Bytes callData = ABIEncoder.encodeApprove(BOB, BigInteger.valueOf(500), BigInteger.ZERO);
        EVMHelper.ExecutionResult result = evm.executeCall(ALICE, IST_COIN_ADDRESS, callData);
        assertTrue(result.isSuccess(), "Approve should succeed");

        // Verify allowance
        callData = ABIEncoder.encodeAllowance(ALICE, BOB);
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, callData);
        BigInteger allowance = evm.extractUint256FromReturnData();
        assertEquals(BigInteger.valueOf(500), allowance, "Allowance should be 500");

        // Bob uses transferFrom to transfer 300 from Alice to himself
        callData = ABIEncoder.encodeTransferFrom(ALICE, BOB, BigInteger.valueOf(300));
        result = evm.executeCall(BOB, IST_COIN_ADDRESS, callData);
        assertTrue(result.isSuccess(), "TransferFrom should succeed");

        // Check remaining allowance (should be 200)
        callData = ABIEncoder.encodeAllowance(ALICE, BOB);
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, callData);
        BigInteger remainingAllowance = evm.extractUint256FromReturnData();
        assertEquals(BigInteger.valueOf(200), remainingAllowance, "Remaining allowance should be 200");
    }

    @Test
    public void testTransferFromInsufficientAllowance() {
        // Alice approves Bob for 100 tokens
        Bytes callData = ABIEncoder.encodeApprove(BOB, BigInteger.valueOf(100), BigInteger.ZERO);
        evm.executeCall(ALICE, IST_COIN_ADDRESS, callData);

        // Bob tries to transferFrom 200 tokens (more than approved)
        callData = ABIEncoder.encodeTransferFrom(ALICE, BOB, BigInteger.valueOf(200));
        EVMHelper.ExecutionResult result = evm.executeCall(BOB, IST_COIN_ADDRESS, callData);

        assertFalse(result.isSuccess(), "TransferFrom should fail with insufficient allowance");
    }

    @Test
    public void testFrontrunningProtection() {
        // Step 1: Alice approves Bob for 200 tokens
        Bytes callData = ABIEncoder.encodeApprove(BOB, BigInteger.valueOf(200), BigInteger.ZERO);
        EVMHelper.ExecutionResult result = evm.executeCall(ALICE, IST_COIN_ADDRESS, callData);
        assertTrue(result.isSuccess(), "Initial approve should succeed");

        // Step 2: Bob drains the allowance via transferFrom
        callData = ABIEncoder.encodeTransferFrom(ALICE, BOB, BigInteger.valueOf(200));
        result = evm.executeCall(BOB, IST_COIN_ADDRESS, callData);
        assertTrue(result.isSuccess(), "TransferFrom should succeed");

        // Verify allowance is now 0
        callData = ABIEncoder.encodeAllowance(ALICE, BOB);
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, callData);
        BigInteger currentAllowance = evm.extractUint256FromReturnData();
        assertEquals(BigInteger.ZERO, currentAllowance, "Allowance should be 0 after drain");

        // Step 3: Alice tries to set allowance to 50, expecting current is 100 (MUST REVERT)
        callData = ABIEncoder.encodeApprove(BOB, BigInteger.valueOf(50), BigInteger.valueOf(100));
        result = evm.executeCall(ALICE, IST_COIN_ADDRESS, callData);
        assertFalse(result.isSuccess(), "Approve with wrong expectedCurrentValue should fail");

        // Verify allowance remains 0 (attack prevented)
        callData = ABIEncoder.encodeAllowance(ALICE, BOB);
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, callData);
        BigInteger allowanceAfterAttack = evm.extractUint256FromReturnData();
        assertEquals(BigInteger.ZERO, allowanceAfterAttack, "Allowance should remain 0, attack prevented");

        // Step 4: Alice retries with correct expectation (MUST SUCCEED)
        callData = ABIEncoder.encodeApprove(BOB, BigInteger.valueOf(50), BigInteger.ZERO);
        result = evm.executeCall(ALICE, IST_COIN_ADDRESS, callData);
        assertTrue(result.isSuccess(), "Approve with correct expectedCurrentValue should succeed");

        // Verify final allowance is 50
        callData = ABIEncoder.encodeAllowance(ALICE, BOB);
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, callData);
        BigInteger finalAllowance = evm.extractUint256FromReturnData();
        assertEquals(BigInteger.valueOf(50), finalAllowance, "Final allowance should be 50");
    }
}

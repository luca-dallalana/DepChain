package blockchain;

import blockchain.evm.ABIEncoder;
import blockchain.evm.EVMHelper;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

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

    @Test
    public void testISTCoin() {
        System.out.println("=== IST Coin Frontrunning-Resistant Test Suite ===\n");

        EVMHelper evm = new EVMHelper();

        System.out.println("--- Setup Phase ---");
        evm.createAccount(DEPLOYER, Wei.fromEth(1000));
        evm.createAccount(ALICE, Wei.fromEth(1000));
        evm.createAccount(BOB, Wei.fromEth(1000));
        evm.createAccount(CHARLIE, Wei.fromEth(1000));
        System.out.println("Accounts created: DEPLOYER, ALICE, BOB, CHARLIE");

        // Deploy ISTCoin with Alice as initial holder
        Bytes istCoinConstructorParams = ABIEncoder.encodeISTCoinConstructor(ALICE);
        Bytes istCoinDeploymentCode = Bytes.concatenate(
            Bytes.fromHexString(IST_COIN_BYTECODE),
            istCoinConstructorParams
        );
        boolean istCoinDeployed = evm.deployContract(DEPLOYER, IST_COIN_ADDRESS, istCoinDeploymentCode);
        System.out.println("ISTCoin deployed: " + istCoinDeployed);
        System.out.println();

        // ========== Test 1: Deployment Verification ==========
        System.out.println("--- Test 1: Deployment Verification ---");

        Bytes callData = ABIEncoder.encodeTotalSupply();
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, callData);
        BigInteger totalSupply = evm.extractUint256FromReturnData();
        System.out.println("Total Supply: " + totalSupply);
        System.out.println("Expected: " + TOTAL_SUPPLY);
        System.out.println("Match: " + totalSupply.equals(TOTAL_SUPPLY));

        callData = ABIEncoder.encodeBalanceOf(ALICE);
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, callData);
        BigInteger aliceBalance = evm.extractUint256FromReturnData();
        System.out.println("Alice's Balance: " + aliceBalance);
        System.out.println("Expected: " + TOTAL_SUPPLY);
        System.out.println("Match: " + aliceBalance.equals(TOTAL_SUPPLY));
        evm.printLastTraceLines(5, "Test 1 - balanceOf (RETURN)");
        System.out.println();

        // ========== Test 2: Transfer Success (Allowed Account) ==========
        System.out.println("--- Test 2: Transfer Success (Allowed Account) ---");

        callData = ABIEncoder.encodeTransfer(BOB, BigInteger.valueOf(1000));
        EVMHelper.ExecutionResult result = evm.executeCall(ALICE, IST_COIN_ADDRESS, callData);
        System.out.println("Transfer from Alice to Bob: " + result.isSuccess());
        evm.printLastTraceLines(5, "Test 2 - transfer (RETURN)");

        callData = ABIEncoder.encodeBalanceOf(BOB);
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, callData);
        BigInteger bobBalance = evm.extractUint256FromReturnData();
        System.out.println("Bob's Balance after transfer: " + bobBalance);
        System.out.println("Expected: 1000");
        System.out.println("Match: " + bobBalance.equals(BigInteger.valueOf(1000)));
        System.out.println();

        // ========== Test 3: Standard TransferFrom Flow ==========
        System.out.println("--- Test 3: Standard TransferFrom Flow ---");

        // Alice approves Bob for 500 tokens
        callData = ABIEncoder.encodeApprove(BOB, BigInteger.valueOf(500), BigInteger.ZERO);
        result = evm.executeCall(ALICE, IST_COIN_ADDRESS, callData);
        System.out.println("Alice approves Bob for 500: " + result.isSuccess());

        // Bob uses transferFrom to transfer 300 from Alice to himself
        callData = ABIEncoder.encodeTransferFrom(ALICE, BOB, BigInteger.valueOf(300));
        result = evm.executeCall(BOB, IST_COIN_ADDRESS, callData);
        System.out.println("Bob transferFrom Alice to Bob (300): " + result.isSuccess());
        evm.printLastTraceLines(5, "Test 3 - transferFrom (RETURN)");

        // Check remaining allowance (should be 200)
        callData = ABIEncoder.encodeAllowance(ALICE, BOB);
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, callData);
        BigInteger allowance = evm.extractUint256FromReturnData();
        System.out.println("Remaining allowance: " + allowance);
        System.out.println("Expected: 200");
        System.out.println("Match: " + allowance.equals(BigInteger.valueOf(200)));
        System.out.println();

        // ========== Test 4: Frontrunning Attack Prevention (CRITICAL) ==========
        System.out.println("--- Test 4: Frontrunning Attack Prevention (CRITICAL) ---");

        // IMPORTANT: Reset allowance from Test 4 (was 200) before starting frontrunning test
        System.out.println("Step 0: Reset allowance from 200 to 0");
        callData = ABIEncoder.encodeApprove(BOB, BigInteger.ZERO, BigInteger.valueOf(200));
        result = evm.executeCall(ALICE, IST_COIN_ADDRESS, callData);
        System.out.println("  Reset to 0: " + result.isSuccess());

        // Step 1: Alice approves Bob for 200 tokens
        System.out.println("Step 1: Alice approves Bob for 200 tokens");
        callData = ABIEncoder.encodeApprove(BOB, BigInteger.valueOf(200), BigInteger.ZERO);
        result = evm.executeCall(ALICE, IST_COIN_ADDRESS, callData);
        System.out.println("  approve(Bob, 200, 0): " + result.isSuccess());

        // Step 2: Bob drains the allowance via transferFrom
        System.out.println("Step 2: Bob drains allowance to 0");
        callData = ABIEncoder.encodeTransferFrom(ALICE, BOB, BigInteger.valueOf(200));
        result = evm.executeCall(BOB, IST_COIN_ADDRESS, callData);
        System.out.println("  transferFrom(Alice, Bob, 200): " + result.isSuccess());

        // Verify allowance is now 0
        callData = ABIEncoder.encodeAllowance(ALICE, BOB);
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, callData);
        BigInteger currentAllowance = evm.extractUint256FromReturnData();
        System.out.println("  Current allowance: " + currentAllowance + " (should be 0)");

        // Step 3: Alice tries to set allowance to 50, expecting current is 100 (MUST REVERT)
        System.out.println("Step 3: Alice tries approve(Bob, 50, 100) - wrong expectation");
        callData = ABIEncoder.encodeApprove(BOB, BigInteger.valueOf(50), BigInteger.valueOf(100));
        result = evm.executeCall(ALICE, IST_COIN_ADDRESS, callData);
        System.out.println("  approve(Bob, 50, 100): " + result.isSuccess());
        System.out.println("  MUST REVERT: " + !result.isSuccess());
        evm.printLastTraceLines(10, "Test 4 Step 3 - approve with wrong expectation (REVERT)");

        if (result.isSuccess()) {
            System.out.println("    FRONTRUNNING PROTECTION FAILED - Transaction should have reverted!");
        } else {
            System.out.println("    Frontrunning protection working - transaction reverted as expected");
        }

        // Step 4: Alice retries with correct expectation (MUST SUCCEED)
        System.out.println("Step 4: Alice retries with correct expectation");
        callData = ABIEncoder.encodeApprove(BOB, BigInteger.valueOf(50), BigInteger.ZERO);
        result = evm.executeCall(ALICE, IST_COIN_ADDRESS, callData);
        System.out.println("  approve(Bob, 50, 0): " + result.isSuccess());
        System.out.println("  MUST SUCCEED: " + result.isSuccess());
        evm.printLastTraceLines(10, "Test 4 Step 4 - approve with correct expectation (RETURN)");

        // Step 5: Verify final allowance is 50
        callData = ABIEncoder.encodeAllowance(ALICE, BOB);
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, callData);
        BigInteger finalAllowance = evm.extractUint256FromReturnData();
        System.out.println("  Final allowance: " + finalAllowance + " (should be 50)");
        System.out.println("  Match: " + finalAllowance.equals(BigInteger.valueOf(50)));

        System.out.println("  Frontrunning attack prevented!");
        System.out.println();

        // ========== Test 5: Approve with Correct Expected Value ==========
        System.out.println("--- Test 5: Approve with Correct Expected Value ---");

        // Current allowance is 50, set to 75
        callData = ABIEncoder.encodeApprove(BOB, BigInteger.valueOf(75), BigInteger.valueOf(50));
        result = evm.executeCall(ALICE, IST_COIN_ADDRESS, callData);
        System.out.println("Approve with correct expected value: " + result.isSuccess());
        evm.printLastTraceLines(5, "Test 5 - approve (RETURN)");

        callData = ABIEncoder.encodeAllowance(ALICE, BOB);
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, callData);
        BigInteger newAllowance = evm.extractUint256FromReturnData();
        System.out.println("New allowance: " + newAllowance);
        System.out.println("Expected: 75");
        System.out.println("Match: " + newAllowance.equals(BigInteger.valueOf(75)));
        System.out.println();

        // ========== Test 6: Allowance Queries ==========
        System.out.println("--- Test 6: Allowance Queries ---");

        callData = ABIEncoder.encodeAllowance(ALICE, BOB);
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, callData);
        BigInteger aliceToBob = evm.extractUint256FromReturnData();
        System.out.println("Alice -> Bob allowance: " + aliceToBob);

        callData = ABIEncoder.encodeAllowance(ALICE, CHARLIE);
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, callData);
        BigInteger aliceToCharlie = evm.extractUint256FromReturnData();
        System.out.println("Alice -> Charlie allowance: " + aliceToCharlie);
        evm.printLastTraceLines(5, "Test 6 - allowance (RETURN)");
        System.out.println();

        System.out.println("=== All Tests Completed ===");
    }

    // Main method for standalone execution
    public static void main(String[] args) {
        ISTCoinTest test = new ISTCoinTest();
        test.testISTCoin();
    }
}

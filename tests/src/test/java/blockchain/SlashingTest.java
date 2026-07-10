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

public class SlashingTest {

    private static final String SLASHING_BYTECODE = BytecodeLoader.loadBytecode("SlashingContract");
    private static final Address DEPLOYER = Address.fromHexString("0x1111111111111111111111111111111111111111");
    private static final Address SLASHING_ADDRESS = Address.fromHexString(Block.SLASHING_CONTRACT_ADDRESS);
    private static final BigInteger VALIDATOR_0 = BigInteger.ZERO;

    private EVMHelper evm;

    @BeforeEach
    public void setup() {
        evm = new EVMHelper();
        evm.createAccount(DEPLOYER, Wei.fromEth(1000));
        boolean deployed = evm.deployContract(DEPLOYER, SLASHING_ADDRESS, Bytes.fromHexString(SLASHING_BYTECODE));
        assertTrue(deployed, "SlashingContract deployment should succeed");
    }

    @Test
    public void testInitialStakeIsZero() {
        evm.executeCall(DEPLOYER, SLASHING_ADDRESS, ABIEncoder.encodeGetStake(VALIDATOR_0));
        BigInteger stake = evm.extractUint256FromReturnData();
        assertEquals(BigInteger.ZERO, stake, "Initial stake should be zero");
    }

    @Test
    public void testInitialIsNotSlashed() {
        evm.executeCall(DEPLOYER, SLASHING_ADDRESS, ABIEncoder.encodeIsSlashed(VALIDATOR_0));
        assertFalse(evm.extractBoolFromReturnData(), "Validator should not be slashed initially");
    }

    @Test
    public void testDeposit() {
        BigInteger amount = BigInteger.valueOf(1000);
        EVMHelper.ExecutionResult result = evm.executeCall(DEPLOYER, SLASHING_ADDRESS,
            ABIEncoder.encodeDeposit(VALIDATOR_0, amount));
        assertTrue(result.isSuccess(), "Deposit should succeed");

        evm.executeCall(DEPLOYER, SLASHING_ADDRESS, ABIEncoder.encodeGetStake(VALIDATOR_0));
        assertEquals(amount, evm.extractUint256FromReturnData(), "Stake should equal deposited amount");
    }

    @Test
    public void testSlashEquivocation() {
        BigInteger amount = BigInteger.valueOf(5000);
        evm.executeCall(DEPLOYER, SLASHING_ADDRESS, ABIEncoder.encodeDeposit(VALIDATOR_0, amount));

        byte[] blockHashA = new byte[32];
        blockHashA[0] = 0x01;
        byte[] blockHashB = new byte[32];
        blockHashB[0] = 0x02;
        byte[] sig = new byte[96];

        EVMHelper.ExecutionResult result = evm.executeCall(DEPLOYER, SLASHING_ADDRESS,
            ABIEncoder.encodeSlash(
                VALIDATOR_0,
                BigInteger.ONE, blockHashA, sig,
                BigInteger.ONE, blockHashB, sig
            ));
        assertTrue(result.isSuccess(), "Slash should succeed for valid equivocation");

        evm.executeCall(DEPLOYER, SLASHING_ADDRESS, ABIEncoder.encodeGetStake(VALIDATOR_0));
        assertEquals(BigInteger.ZERO, evm.extractUint256FromReturnData(), "Stake should be zero after slash");

        evm.executeCall(DEPLOYER, SLASHING_ADDRESS, ABIEncoder.encodeIsSlashed(VALIDATOR_0));
        assertTrue(evm.extractBoolFromReturnData(), "Validator should be marked as slashed");
    }

    @Test
    public void testSlashFailsSameBlockHash() {
        byte[] blockHash = new byte[32];
        blockHash[0] = 0x01;
        byte[] sig = new byte[96];

        EVMHelper.ExecutionResult result = evm.executeCall(DEPLOYER, SLASHING_ADDRESS,
            ABIEncoder.encodeSlash(
                VALIDATOR_0,
                BigInteger.ONE, blockHash, sig,
                BigInteger.ONE, blockHash, sig
            ));
        assertFalse(result.isSuccess(), "Slash should fail when block hashes are the same");
    }

    @Test
    public void testSlashFailsDifferentViews() {
        byte[] blockHashA = new byte[32];
        blockHashA[0] = 0x01;
        byte[] blockHashB = new byte[32];
        blockHashB[0] = 0x02;
        byte[] sig = new byte[96];

        EVMHelper.ExecutionResult result = evm.executeCall(DEPLOYER, SLASHING_ADDRESS,
            ABIEncoder.encodeSlash(
                VALIDATOR_0,
                BigInteger.ONE, blockHashA, sig,
                BigInteger.valueOf(2), blockHashB, sig
            ));
        assertFalse(result.isSuccess(), "Slash should fail when views differ");
    }

    @Test
    public void testCannotSlashTwice() {
        byte[] blockHashA = new byte[32];
        blockHashA[0] = 0x01;
        byte[] blockHashB = new byte[32];
        blockHashB[0] = 0x02;
        byte[] sig = new byte[96];

        Bytes callData = ABIEncoder.encodeSlash(
            VALIDATOR_0,
            BigInteger.ONE, blockHashA, sig,
            BigInteger.ONE, blockHashB, sig
        );
        evm.executeCall(DEPLOYER, SLASHING_ADDRESS, callData);

        EVMHelper.ExecutionResult result = evm.executeCall(DEPLOYER, SLASHING_ADDRESS, callData);
        assertFalse(result.isSuccess(), "Cannot slash an already-slashed validator");
    }

    @Test
    public void testSlashFailsInvalidSignatureLength() {
        byte[] blockHashA = new byte[32];
        blockHashA[0] = 0x01;
        byte[] blockHashB = new byte[32];
        blockHashB[0] = 0x02;
        byte[] shortSig = new byte[48];

        EVMHelper.ExecutionResult result = evm.executeCall(DEPLOYER, SLASHING_ADDRESS,
            ABIEncoder.encodeSlash(
                VALIDATOR_0,
                BigInteger.ONE, blockHashA, shortSig,
                BigInteger.ONE, blockHashB, shortSig
            ));
        assertFalse(result.isSuccess(), "Slash should fail with non-96-byte signatures");
    }
}

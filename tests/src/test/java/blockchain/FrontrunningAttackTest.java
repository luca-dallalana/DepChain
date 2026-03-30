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

public class FrontrunningAttackTest {

    private static final String IST_COIN_BYTECODE = BytecodeLoader.loadBytecode("ISTCoin");

    private static final Address ALICE = Address.fromHexString("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    private static final Address BOB_MALICIOUS = Address.fromHexString("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    private static final Address DEPLOYER = Address.fromHexString("0x1111111111111111111111111111111111111111");
    private static final Address IST_COIN_ADDRESS = Address.fromHexString("0x5555555555555555555555555555555555555555");

    private EVMHelper evm;

    @BeforeEach
    public void setup() {
        evm = new EVMHelper();

        evm.createAccount(DEPLOYER, Wei.fromEth(1000000));
        evm.createAccount(ALICE, Wei.fromEth(1000000));
        evm.createAccount(BOB_MALICIOUS, Wei.fromEth(1000000));

        Bytes istCoinConstructorParams = ABIEncoder.encodeISTCoinConstructor(ALICE, BOB_MALICIOUS);
        Bytes istCoinDeploymentCode = Bytes.concatenate(Bytes.fromHexString(IST_COIN_BYTECODE), istCoinConstructorParams);
        boolean deployed = evm.deployContract(DEPLOYER, IST_COIN_ADDRESS, istCoinDeploymentCode);
        assertTrue(deployed, "ISTCoin deployment should succeed");
    }

    @Test
    public void testApproveProtectionMechanism() {

        Bytes callData = ABIEncoder.encodeApprove(BOB_MALICIOUS, BigInteger.valueOf(100), BigInteger.ZERO);
        EVMHelper.ExecutionResult result = evm.executeCall(ALICE, IST_COIN_ADDRESS, callData);
        assertTrue(result.isSuccess());

        callData = ABIEncoder.encodeTransferFrom(ALICE, BOB_MALICIOUS, BigInteger.valueOf(100));
        result = evm.executeCall(BOB_MALICIOUS, IST_COIN_ADDRESS, callData);
        assertTrue(result.isSuccess());

        callData = ABIEncoder.encodeApprove(BOB_MALICIOUS, BigInteger.valueOf(50), BigInteger.valueOf(100));
        result = evm.executeCall(ALICE, IST_COIN_ADDRESS, callData);

        assertFalse(result.isSuccess(), "approve() rejects when expectedCurrentValue doesn't match");

        callData = ABIEncoder.encodeAllowance(ALICE, BOB_MALICIOUS);
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, callData);
        BigInteger finalAllowance = evm.extractUint256FromReturnData();
        assertEquals(BigInteger.ZERO, finalAllowance, "Allowance remains 0, attack prevented");
    }
}

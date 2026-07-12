package blockchain;

import blockchain.evm.ABIEncoder;
import blockchain.evm.EVMHelper;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class OracleTest {

    private static final String ORACLE_BYTECODE = BytecodeLoader.loadBytecode("PriceOracle");

    private static final Address DEPLOYER = Address.fromHexString("0x1111111111111111111111111111111111111111");
    private static final Address ADMIN    = Address.fromHexString("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    private static final Address ALICE    = Address.fromHexString("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab");
    private static final Address BOB      = Address.fromHexString("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

    private static final Address ORACLE_ADDRESS = Address.fromHexString("0x9999999999999999999999999999999999999999");

    private static final byte[] IST_DEP_KEY = Arrays.copyOf("IST/DEP".getBytes(StandardCharsets.UTF_8), 32);
    private static final byte[] DEP_USD_KEY = Arrays.copyOf("DEP/USD".getBytes(StandardCharsets.UTF_8), 32);

    private EVMHelper evm;

    @BeforeEach
    public void setup() {
        evm = new EVMHelper();

        evm.createAccount(DEPLOYER, Wei.fromEth(1000));
        evm.createAccount(ADMIN,    Wei.fromEth(1000));
        evm.createAccount(ALICE,    Wei.fromEth(1000));
        evm.createAccount(BOB,      Wei.fromEth(1000));

        Bytes oracleCode = Bytes.concatenate(
            Bytes.fromHexString(ORACLE_BYTECODE),
            ABIEncoder.encodeOracleConstructor(ADMIN)
        );
        assertTrue(evm.deployContract(DEPLOYER, ORACLE_ADDRESS, oracleCode), "PriceOracle deploy failed");
    }

    private BigInteger[] getPrice(byte[] key) {
        evm.executeCall(DEPLOYER, ORACLE_ADDRESS, ABIEncoder.encodeGetPrice(key));
        return new BigInteger[]{
            evm.extractUint256FromReturnData(0),
            evm.extractUint256FromReturnData(1)
        };
    }

    @Test
    public void testInitialPriceIsZero() {
        BigInteger[] feed = getPrice(IST_DEP_KEY);
        assertEquals(BigInteger.ZERO, feed[0], "price should be 0 before any update");
        assertEquals(BigInteger.ZERO, feed[1], "timestamp should be 0 before any update");
    }

    @Test
    public void testAdminIsDefaultReporter() {
        EVMHelper.ExecutionResult r = evm.executeCall(ADMIN, ORACLE_ADDRESS,
            ABIEncoder.encodeUpdatePrice(IST_DEP_KEY, BigInteger.valueOf(1000L), BigInteger.valueOf(1000000L)));
        assertTrue(r.isSuccess(), "admin should be able to call updatePrice by default");
    }

    @Test
    public void testUpdatePrice() {
        BigInteger price = BigInteger.valueOf(42_000L);
        BigInteger timestamp = BigInteger.valueOf(1_700_000_000L);

        EVMHelper.ExecutionResult r = evm.executeCall(ADMIN, ORACLE_ADDRESS,
            ABIEncoder.encodeUpdatePrice(IST_DEP_KEY, price, timestamp));
        assertTrue(r.isSuccess(), "updatePrice should succeed");

        BigInteger[] feed = getPrice(IST_DEP_KEY);
        assertEquals(price, feed[0], "stored price should match");
        assertEquals(timestamp, feed[1], "stored timestamp should match");
    }

    @Test
    public void testUpdatePriceOverwrites() {
        evm.executeCall(ADMIN, ORACLE_ADDRESS,
            ABIEncoder.encodeUpdatePrice(IST_DEP_KEY, BigInteger.valueOf(100L), BigInteger.valueOf(1000L)));
        evm.executeCall(ADMIN, ORACLE_ADDRESS,
            ABIEncoder.encodeUpdatePrice(IST_DEP_KEY, BigInteger.valueOf(200L), BigInteger.valueOf(2000L)));

        BigInteger[] feed = getPrice(IST_DEP_KEY);
        assertEquals(BigInteger.valueOf(200L), feed[0], "second update should overwrite price");
        assertEquals(BigInteger.valueOf(2000L), feed[1], "second update should overwrite timestamp");
    }

    @Test
    public void testGetPriceMultipleFeeds() {
        evm.executeCall(ADMIN, ORACLE_ADDRESS,
            ABIEncoder.encodeUpdatePrice(IST_DEP_KEY, BigInteger.valueOf(500L), BigInteger.valueOf(1000L)));
        evm.executeCall(ADMIN, ORACLE_ADDRESS,
            ABIEncoder.encodeUpdatePrice(DEP_USD_KEY, BigInteger.valueOf(999L), BigInteger.valueOf(2000L)));

        BigInteger[] istDep = getPrice(IST_DEP_KEY);
        BigInteger[] depUsd = getPrice(DEP_USD_KEY);

        assertEquals(BigInteger.valueOf(500L), istDep[0], "IST/DEP price should be independent");
        assertEquals(BigInteger.valueOf(999L), depUsd[0], "DEP/USD price should be independent");
        assertEquals(BigInteger.valueOf(1000L), istDep[1], "IST/DEP timestamp should be independent");
        assertEquals(BigInteger.valueOf(2000L), depUsd[1], "DEP/USD timestamp should be independent");
    }

    @Test
    public void testNonReporterCannotUpdate() {
        EVMHelper.ExecutionResult r = evm.executeCall(BOB, ORACLE_ADDRESS,
            ABIEncoder.encodeUpdatePrice(IST_DEP_KEY, BigInteger.valueOf(1L), BigInteger.valueOf(1L)));
        assertFalse(r.isSuccess(), "non-reporter should be rejected");
    }

    @Test
    public void testAddReporter() {
        evm.executeCall(ADMIN, ORACLE_ADDRESS, ABIEncoder.encodeAddReporter(ALICE));

        EVMHelper.ExecutionResult r = evm.executeCall(ALICE, ORACLE_ADDRESS,
            ABIEncoder.encodeUpdatePrice(IST_DEP_KEY, BigInteger.valueOf(77L), BigInteger.valueOf(5000L)));
        assertTrue(r.isSuccess(), "newly added reporter should be able to updatePrice");

        BigInteger[] feed = getPrice(IST_DEP_KEY);
        assertEquals(BigInteger.valueOf(77L), feed[0], "price from new reporter should be stored");
    }

    @Test
    public void testRemoveReporter() {
        evm.executeCall(ADMIN, ORACLE_ADDRESS, ABIEncoder.encodeAddReporter(ALICE));
        evm.executeCall(ADMIN, ORACLE_ADDRESS, ABIEncoder.encodeRemoveReporter(ALICE));

        EVMHelper.ExecutionResult r = evm.executeCall(ALICE, ORACLE_ADDRESS,
            ABIEncoder.encodeUpdatePrice(IST_DEP_KEY, BigInteger.valueOf(1L), BigInteger.valueOf(1L)));
        assertFalse(r.isSuccess(), "removed reporter should be rejected");
    }

    @Test
    public void testNonAdminCannotAddReporter() {
        EVMHelper.ExecutionResult r = evm.executeCall(BOB, ORACLE_ADDRESS,
            ABIEncoder.encodeAddReporter(ALICE));
        assertFalse(r.isSuccess(), "non-admin should not be able to addReporter");
    }

    @Test
    public void testIsReporterQuery() {
        evm.executeCall(DEPLOYER, ORACLE_ADDRESS, ABIEncoder.encodeIsReporter(ADMIN));
        assertTrue(evm.extractBoolFromReturnData(), "admin should be a reporter");

        evm.executeCall(DEPLOYER, ORACLE_ADDRESS, ABIEncoder.encodeIsReporter(BOB));
        assertFalse(evm.extractBoolFromReturnData(), "BOB should not be a reporter");
    }
}

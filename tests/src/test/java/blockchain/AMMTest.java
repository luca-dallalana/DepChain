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

public class AMMTest {

    private static final String IST_COIN_BYTECODE = BytecodeLoader.loadBytecode("ISTCoin");
    private static final String DEP_TOKEN_BYTECODE = BytecodeLoader.loadBytecode("DepToken");
    private static final String AMM_BYTECODE = BytecodeLoader.loadBytecode("AMM");

    private static final Address DEPLOYER = Address.fromHexString("0x1111111111111111111111111111111111111111");
    private static final Address ALICE   = Address.fromHexString("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    private static final Address BOB     = Address.fromHexString("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    private static final Address CHARLIE = Address.fromHexString("0xcccccccccccccccccccccccccccccccccccccccc");

    private static final Address IST_COIN_ADDRESS  = Address.fromHexString("0x5555555555555555555555555555555555555555");
    private static final Address DEP_TOKEN_ADDRESS = Address.fromHexString("0x6666666666666666666666666666666666666666");
    private static final Address AMM_ADDRESS       = Address.fromHexString("0x8888888888888888888888888888888888888888");

    private static final BigInteger TOTAL_SUPPLY     = new BigInteger("10000000000");
    private static final BigInteger HALF_SUPPLY      = TOTAL_SUPPLY.divide(BigInteger.TWO);
    private static final BigInteger LIQUIDITY_AMOUNT = BigInteger.valueOf(1_000_000L);

    private EVMHelper evm;

    @BeforeEach
    public void setup() {
        evm = new EVMHelper();

        evm.createAccount(DEPLOYER, Wei.fromEth(1000));
        evm.createAccount(ALICE,    Wei.fromEth(1000));
        evm.createAccount(BOB,      Wei.fromEth(1000));
        evm.createAccount(CHARLIE,  Wei.fromEth(1000));

        Bytes istCode = Bytes.concatenate(
            Bytes.fromHexString(IST_COIN_BYTECODE),
            ABIEncoder.encodeISTCoinConstructor(ALICE, BOB)
        );
        assertTrue(evm.deployContract(DEPLOYER, IST_COIN_ADDRESS, istCode), "ISTCoin deploy failed");

        Bytes depCode = Bytes.concatenate(
            Bytes.fromHexString(DEP_TOKEN_BYTECODE),
            ABIEncoder.encodeDepTokenConstructor(ALICE, BOB)
        );
        assertTrue(evm.deployContract(DEPLOYER, DEP_TOKEN_ADDRESS, depCode), "DepToken deploy failed");

        Bytes ammCode = Bytes.concatenate(
            Bytes.fromHexString(AMM_BYTECODE),
            ABIEncoder.encodeAMMConstructor(IST_COIN_ADDRESS, DEP_TOKEN_ADDRESS)
        );
        assertTrue(evm.deployContract(DEPLOYER, AMM_ADDRESS, ammCode), "AMM deploy failed");
    }

    private void approveIST(Address from, BigInteger amount) {
        EVMHelper.ExecutionResult r = evm.executeCall(from, IST_COIN_ADDRESS,
            ABIEncoder.encodeApprove(AMM_ADDRESS, amount, BigInteger.ZERO));
        assertTrue(r.isSuccess(), "ISTCoin approve failed");
    }

    private void approveDEP(Address from, BigInteger amount) {
        EVMHelper.ExecutionResult r = evm.executeCall(from, DEP_TOKEN_ADDRESS,
            ABIEncoder.encodeDepTokenApprove(AMM_ADDRESS, amount));
        assertTrue(r.isSuccess(), "DepToken approve failed");
    }

    private void addInitialLiquidity() {
        approveIST(ALICE, LIQUIDITY_AMOUNT);
        approveDEP(ALICE, LIQUIDITY_AMOUNT);
        EVMHelper.ExecutionResult r = evm.executeCall(ALICE, AMM_ADDRESS,
            ABIEncoder.encodeAddLiquidity(LIQUIDITY_AMOUNT, LIQUIDITY_AMOUNT));
        assertTrue(r.isSuccess(), "addLiquidity failed");
    }

    private BigInteger[] getReserves() {
        evm.executeCall(ALICE, AMM_ADDRESS, ABIEncoder.encodeGetReserves());
        return new BigInteger[]{
            evm.extractUint256FromReturnData(0),
            evm.extractUint256FromReturnData(1)
        };
    }

    private BigInteger getLiquidityOf(Address provider) {
        evm.executeCall(DEPLOYER, AMM_ADDRESS, ABIEncoder.encodeGetLiquidityOf(provider));
        return evm.extractUint256FromReturnData();
    }

    private BigInteger getISTBalance(Address account) {
        evm.executeCall(DEPLOYER, IST_COIN_ADDRESS, ABIEncoder.encodeBalanceOf(account));
        return evm.extractUint256FromReturnData();
    }

    private BigInteger getDEPBalance(Address account) {
        evm.executeCall(DEPLOYER, DEP_TOKEN_ADDRESS, ABIEncoder.encodeBalanceOf(account));
        return evm.extractUint256FromReturnData();
    }

    @Test
    public void testInitialReservesAreZero() {
        BigInteger[] reserves = getReserves();
        assertEquals(BigInteger.ZERO, reserves[0], "reserve0 should be 0 before any liquidity");
        assertEquals(BigInteger.ZERO, reserves[1], "reserve1 should be 0 before any liquidity");
        assertEquals(BigInteger.ZERO, getLiquidityOf(ALICE), "ALICE LP should be 0 before deposit");
    }

    @Test
    public void testAddInitialLiquidity() {
        addInitialLiquidity();

        BigInteger expectedLP = BigInteger.valueOf(1_000_000L); // sqrt(1M * 1M) = 1M
        assertEquals(expectedLP, getLiquidityOf(ALICE), "ALICE LP should equal sqrt(amount0 * amount1)");

        BigInteger[] reserves = getReserves();
        assertEquals(LIQUIDITY_AMOUNT, reserves[0], "reserve0 should equal deposited IST");
        assertEquals(LIQUIDITY_AMOUNT, reserves[1], "reserve1 should equal deposited DEP");

        assertEquals(LIQUIDITY_AMOUNT, getISTBalance(AMM_ADDRESS), "AMM IST balance should equal reserve0");
        assertEquals(LIQUIDITY_AMOUNT, getDEPBalance(AMM_ADDRESS), "AMM DEP balance should equal reserve1");
    }

    @Test
    public void testAddProportionalLiquidity() {
        addInitialLiquidity();

        BigInteger bobAmount = LIQUIDITY_AMOUNT.multiply(BigInteger.TWO);
        approveIST(BOB, bobAmount);
        approveDEP(BOB, bobAmount);
        EVMHelper.ExecutionResult r = evm.executeCall(BOB, AMM_ADDRESS,
            ABIEncoder.encodeAddLiquidity(bobAmount, bobAmount));
        assertTrue(r.isSuccess(), "BOB addLiquidity should succeed");

        BigInteger expectedBobLP = LIQUIDITY_AMOUNT.multiply(BigInteger.TWO); // min(2M*1M/1M, 2M*1M/1M) = 2M
        assertEquals(expectedBobLP, getLiquidityOf(BOB), "BOB LP should be proportional to contribution");

        BigInteger[] reserves = getReserves();
        assertEquals(LIQUIDITY_AMOUNT.multiply(BigInteger.valueOf(3)), reserves[0], "reserve0 should be 3M total");
        assertEquals(LIQUIDITY_AMOUNT.multiply(BigInteger.valueOf(3)), reserves[1], "reserve1 should be 3M total");
    }

    @Test
    public void testRemoveLiquidity() {
        addInitialLiquidity();

        BigInteger halfLP = LIQUIDITY_AMOUNT.divide(BigInteger.TWO);
        BigInteger aliceISTBefore = getISTBalance(ALICE);

        EVMHelper.ExecutionResult r = evm.executeCall(ALICE, AMM_ADDRESS,
            ABIEncoder.encodeRemoveLiquidity(halfLP));
        assertTrue(r.isSuccess(), "removeLiquidity should succeed");

        assertEquals(halfLP, getLiquidityOf(ALICE), "ALICE LP should be halved");

        BigInteger[] reserves = getReserves();
        assertEquals(halfLP, reserves[0], "reserve0 should be halved");
        assertEquals(halfLP, reserves[1], "reserve1 should be halved");

        assertEquals(aliceISTBefore.add(halfLP), getISTBalance(ALICE), "ALICE should receive back IST proportional to LP burned");
    }

    @Test
    public void testSwapToken0ForToken1() {
        addInitialLiquidity();

        BigInteger amountIn = BigInteger.valueOf(1_000L);
        BigInteger bobDEPBefore = getDEPBalance(BOB);

        approveIST(BOB, amountIn);
        EVMHelper.ExecutionResult r = evm.executeCall(BOB, AMM_ADDRESS,
            ABIEncoder.encodeSwap(IST_COIN_ADDRESS, amountIn, BigInteger.ZERO));
        assertTrue(r.isSuccess(), "swap IST->DEP should succeed");
        BigInteger amountOut = evm.extractUint256FromReturnData();

        assertTrue(amountOut.compareTo(BigInteger.ZERO) > 0, "amountOut should be positive");

        assertEquals(bobDEPBefore.add(amountOut), getDEPBalance(BOB), "BOB DEP balance should increase by amountOut");

        BigInteger[] reserves = getReserves();
        assertEquals(LIQUIDITY_AMOUNT.add(amountIn), reserves[0], "reserve0 should increase by amountIn");
        assertEquals(LIQUIDITY_AMOUNT.subtract(amountOut), reserves[1], "reserve1 should decrease by amountOut");
    }

    @Test
    public void testSwapToken1ForToken0() {
        addInitialLiquidity();

        BigInteger amountIn = BigInteger.valueOf(1_000L);
        BigInteger bobISTBefore = getISTBalance(BOB);

        approveDEP(BOB, amountIn);
        EVMHelper.ExecutionResult r = evm.executeCall(BOB, AMM_ADDRESS,
            ABIEncoder.encodeSwap(DEP_TOKEN_ADDRESS, amountIn, BigInteger.ZERO));
        assertTrue(r.isSuccess(), "swap DEP->IST should succeed");
        BigInteger amountOut = evm.extractUint256FromReturnData();

        assertTrue(amountOut.compareTo(BigInteger.ZERO) > 0, "amountOut should be positive");

        assertEquals(bobISTBefore.add(amountOut), getISTBalance(BOB), "BOB IST balance should increase by amountOut");

        BigInteger[] reserves = getReserves();
        assertEquals(LIQUIDITY_AMOUNT.subtract(amountOut), reserves[0], "reserve0 should decrease by amountOut");
        assertEquals(LIQUIDITY_AMOUNT.add(amountIn), reserves[1], "reserve1 should increase by amountIn");
    }

    @Test
    public void testKConstantHoldsAfterSwap() {
        addInitialLiquidity();

        BigInteger kBefore = LIQUIDITY_AMOUNT.multiply(LIQUIDITY_AMOUNT);

        BigInteger amountIn = BigInteger.valueOf(50_000L);
        approveIST(BOB, amountIn);
        evm.executeCall(BOB, AMM_ADDRESS,
            ABIEncoder.encodeSwap(IST_COIN_ADDRESS, amountIn, BigInteger.ZERO));

        BigInteger[] reserves = getReserves();
        BigInteger kAfter = reserves[0].multiply(reserves[1]);

        assertTrue(kAfter.compareTo(kBefore) >= 0,
            "k should not decrease after swap (fee stays in pool): kBefore=" + kBefore + " kAfter=" + kAfter);
    }

    @Test
    public void testSwapFailsBelowMinAmountOut() {
        addInitialLiquidity();

        BigInteger amountIn = BigInteger.valueOf(1_000L);
        // Expected output is ~996; requiring 2000 should revert
        BigInteger unreachableMinOut = BigInteger.valueOf(2_000L);

        approveIST(BOB, amountIn);
        EVMHelper.ExecutionResult r = evm.executeCall(BOB, AMM_ADDRESS,
            ABIEncoder.encodeSwap(IST_COIN_ADDRESS, amountIn, unreachableMinOut));

        assertFalse(r.isSuccess(), "swap should revert when output < minAmountOut");
    }

    @Test
    public void testRemoveTooMuchLiquidityReverts() {
        addInitialLiquidity();

        BigInteger excessLP = LIQUIDITY_AMOUNT.multiply(BigInteger.TWO);
        EVMHelper.ExecutionResult r = evm.executeCall(ALICE, AMM_ADDRESS,
            ABIEncoder.encodeRemoveLiquidity(excessLP));

        assertFalse(r.isSuccess(), "removeLiquidity should revert when LP amount exceeds balance");
    }

    @Test
    public void testPriceImpact() {
        addInitialLiquidity();

        BigInteger smallIn = BigInteger.valueOf(1_000L);
        approveIST(BOB, smallIn);
        evm.executeCall(BOB, AMM_ADDRESS,
            ABIEncoder.encodeSwap(IST_COIN_ADDRESS, smallIn, BigInteger.ZERO));
        BigInteger smallOut = evm.extractUint256FromReturnData();

        BigInteger largeIn = BigInteger.valueOf(100_000L);
        approveIST(BOB, largeIn);
        evm.executeCall(BOB, AMM_ADDRESS,
            ABIEncoder.encodeSwap(IST_COIN_ADDRESS, largeIn, BigInteger.ZERO));
        BigInteger largeOut = evm.extractUint256FromReturnData();

        // Per-unit comparison: smallOut/smallIn > largeOut/largeIn
        // Cross-multiply: smallOut * largeIn > largeOut * smallIn
        BigInteger lhs = smallOut.multiply(largeIn);
        BigInteger rhs = largeOut.multiply(smallIn);
        assertTrue(lhs.compareTo(rhs) > 0,
            "Small swap should have better per-unit price than large swap: " + lhs + " > " + rhs);
    }
}

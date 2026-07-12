package blockchain.evm;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ABIEncoder {

    private static org.web3j.abi.datatypes.Address toWeb3jAddress(Address addr) {
        return new org.web3j.abi.datatypes.Address(addr.toHexString());
    }

    private static Bytes encodeFunctionCall(String name, List<Type> params) {
        Function function = new Function(name, params, Collections.emptyList());
        return Bytes.fromHexString(FunctionEncoder.encode(function).substring(2));
    }


    public static Bytes encodeISTCoinConstructor(Address initialHolderA, Address initialHolderB) {
        String encoded = FunctionEncoder.encodeConstructor(
            Arrays.asList(
                toWeb3jAddress(initialHolderA),
                toWeb3jAddress(initialHolderB)
            )
        );
        return Bytes.fromHexString(encoded);
    }

    public static Bytes encodeBalanceOf(Address account) {
        return encodeFunctionCall("balanceOf", Arrays.asList(toWeb3jAddress(account)));
    }

    public static Bytes encodeTransfer(Address to, BigInteger amount) {
        return encodeFunctionCall("transfer", Arrays.asList(toWeb3jAddress(to), new Uint256(amount)));
    }

    public static Bytes encodeApprove(Address spender, BigInteger newValue, BigInteger expectedCurrentValue) {
        return encodeFunctionCall("approve", Arrays.asList(
            toWeb3jAddress(spender),
            new Uint256(newValue),
            new Uint256(expectedCurrentValue)
        ));
    }

    public static Bytes encodeTransferFrom(Address from, Address to, BigInteger amount) {
        return encodeFunctionCall("transferFrom", Arrays.asList(
            toWeb3jAddress(from),
            toWeb3jAddress(to),
            new Uint256(amount)
        ));
    }

    public static Bytes encodeAllowance(Address owner, Address spender) {
        return encodeFunctionCall("allowance", Arrays.asList(
            toWeb3jAddress(owner),
            toWeb3jAddress(spender)
        ));
    }


    public static Bytes encodeTotalSupply() {
        return encodeFunctionCall("totalSupply", Collections.emptyList());
    }

    public static Bytes encodeDeposit(BigInteger validatorId, BigInteger amount) {
        return encodeFunctionCall("deposit", Arrays.asList(
            new Uint256(validatorId),
            new Uint256(amount)
        ));
    }

    public static Bytes encodeSlash(
            BigInteger validatorId,
            BigInteger viewA, byte[] blockHashA, byte[] sigA,
            BigInteger viewB, byte[] blockHashB, byte[] sigB) {
        return encodeFunctionCall("slash", Arrays.asList(
            new Uint256(validatorId),
            new Uint256(viewA),
            new Bytes32(blockHashA),
            new DynamicBytes(sigA),
            new Uint256(viewB),
            new Bytes32(blockHashB),
            new DynamicBytes(sigB)
        ));
    }

    public static Bytes encodeGetStake(BigInteger validatorId) {
        return encodeFunctionCall("getStake", Arrays.asList(new Uint256(validatorId)));
    }

    public static Bytes encodeIsSlashed(BigInteger validatorId) {
        return encodeFunctionCall("isSlashed", Arrays.asList(new Uint256(validatorId)));
    }

    public static Bytes encodeDepTokenConstructor(Address holderA, Address holderB) {
        String encoded = FunctionEncoder.encodeConstructor(
            Arrays.asList(
                toWeb3jAddress(holderA),
                toWeb3jAddress(holderB)
            )
        );
        return Bytes.fromHexString(encoded);
    }

    public static Bytes encodeDepTokenApprove(Address spender, BigInteger amount) {
        return encodeFunctionCall("approve", Arrays.asList(
            toWeb3jAddress(spender),
            new Uint256(amount)
        ));
    }

    public static Bytes encodeAMMConstructor(Address token0, Address token1) {
        String encoded = FunctionEncoder.encodeConstructor(
            Arrays.asList(
                toWeb3jAddress(token0),
                toWeb3jAddress(token1)
            )
        );
        return Bytes.fromHexString(encoded);
    }

    public static Bytes encodeAddLiquidity(BigInteger amount0, BigInteger amount1) {
        return encodeFunctionCall("addLiquidity", Arrays.asList(
            new Uint256(amount0),
            new Uint256(amount1)
        ));
    }

    public static Bytes encodeRemoveLiquidity(BigInteger lpAmount) {
        return encodeFunctionCall("removeLiquidity", Arrays.asList(new Uint256(lpAmount)));
    }

    public static Bytes encodeSwap(Address tokenIn, BigInteger amountIn, BigInteger minAmountOut) {
        return encodeFunctionCall("swap", Arrays.asList(
            toWeb3jAddress(tokenIn),
            new Uint256(amountIn),
            new Uint256(minAmountOut)
        ));
    }

    public static Bytes encodeGetReserves() {
        return encodeFunctionCall("getReserves", Collections.emptyList());
    }

    public static Bytes encodeGetLiquidityOf(Address provider) {
        return encodeFunctionCall("getLiquidityOf", Arrays.asList(toWeb3jAddress(provider)));
    }

    public static Bytes encodeOracleConstructor(Address admin) {
        String encoded = FunctionEncoder.encodeConstructor(
            Arrays.asList(toWeb3jAddress(admin))
        );
        return Bytes.fromHexString(encoded);
    }

    public static Bytes encodeUpdatePrice(byte[] key, BigInteger price, BigInteger timestamp) {
        return encodeFunctionCall("updatePrice", Arrays.asList(
            new Bytes32(key),
            new Uint256(price),
            new Uint256(timestamp)
        ));
    }

    public static Bytes encodeGetPrice(byte[] key) {
        return encodeFunctionCall("getPrice", Arrays.asList(new Bytes32(key)));
    }

    public static Bytes encodeAddReporter(Address reporter) {
        return encodeFunctionCall("addReporter", Arrays.asList(toWeb3jAddress(reporter)));
    }

    public static Bytes encodeRemoveReporter(Address reporter) {
        return encodeFunctionCall("removeReporter", Arrays.asList(toWeb3jAddress(reporter)));
    }

    public static Bytes encodeIsReporter(Address account) {
        return encodeFunctionCall("isReporter", Arrays.asList(toWeb3jAddress(account)));
    }

    public static Bytes encodeMultisigConstructor(List<Address> owners, BigInteger threshold) {
        List<org.web3j.abi.datatypes.Address> web3jOwners = owners.stream()
            .map(ABIEncoder::toWeb3jAddress)
            .collect(Collectors.toList());
        String encoded = FunctionEncoder.encodeConstructor(
            Arrays.asList(
                new DynamicArray<>(org.web3j.abi.datatypes.Address.class, web3jOwners),
                new Uint256(threshold)
            )
        );
        return Bytes.fromHexString(encoded);
    }

    public static Bytes encodeSubmit(Address token, Address to, BigInteger amount) {
        return encodeFunctionCall("submit", Arrays.asList(
            toWeb3jAddress(token),
            toWeb3jAddress(to),
            new Uint256(amount)
        ));
    }

    public static Bytes encodeConfirm(BigInteger txId) {
        return encodeFunctionCall("confirm", Arrays.asList(new Uint256(txId)));
    }

    public static Bytes encodeRevoke(BigInteger txId) {
        return encodeFunctionCall("revoke", Arrays.asList(new Uint256(txId)));
    }

    public static Bytes encodeExecute(BigInteger txId) {
        return encodeFunctionCall("execute", Arrays.asList(new Uint256(txId)));
    }

    public static Bytes encodeGetTransaction(BigInteger txId) {
        return encodeFunctionCall("getTransaction", Arrays.asList(new Uint256(txId)));
    }

    public static Bytes encodeGetTransactionCount() {
        return encodeFunctionCall("getTransactionCount", Collections.emptyList());
    }

    public static Bytes encodeIsConfirmedBy(BigInteger txId, Address owner) {
        return encodeFunctionCall("isConfirmedBy", Arrays.asList(
            new Uint256(txId),
            toWeb3jAddress(owner)
        ));
    }
}

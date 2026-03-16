package blockchain.evm;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
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

    public static Bytes encodeAccessControlConstructor(Address[] initialAllowed) {
        List<org.web3j.abi.datatypes.Address> addressList = Arrays.stream(initialAllowed)
            .map(ABIEncoder::toWeb3jAddress)
            .collect(Collectors.toList());

        String encoded = FunctionEncoder.encodeConstructor(
            Arrays.asList(new DynamicArray<>(org.web3j.abi.datatypes.Address.class, addressList))
        );
        return Bytes.fromHexString(encoded);
    }

    public static Bytes encodeISTCoinConstructor(Address accessControlAddress, Address initialHolder) {
        String encoded = FunctionEncoder.encodeConstructor(
            Arrays.asList(toWeb3jAddress(accessControlAddress), toWeb3jAddress(initialHolder))
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

    public static Bytes encodeCanTransfer(Address account) {
        return encodeFunctionCall("canTransfer", Arrays.asList(toWeb3jAddress(account)));
    }

    public static Bytes encodeTotalSupply() {
        return encodeFunctionCall("totalSupply", Collections.emptyList());
    }
}

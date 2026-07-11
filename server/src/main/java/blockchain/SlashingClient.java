package blockchain;

import blockchain.evm.ABIEncoder;
import model.Message;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import java.math.BigInteger;

public class SlashingClient {

    public static Transaction buildSlashingTransaction(
            int validatorId, Message msgA, Message msgB,
            Address sender, int senderNonce) {
        byte[] blockHashA = Bytes.fromHexString(msgA.blockHash).toArray();
        byte[] blockHashB = Bytes.fromHexString(msgB.blockHash).toArray();

        Bytes callData = ABIEncoder.encodeSlash(
            BigInteger.valueOf(validatorId),
            BigInteger.valueOf(msgA.viewNumber), blockHashA, msgA.partialSig,
            BigInteger.valueOf(msgB.viewNumber), blockHashB, msgB.partialSig
        );

        return new Transaction(
            -1,
            sender,
            Address.fromHexString(Block.SLASHING_CONTRACT_ADDRESS),
            0,
            callData.toArray(),
            200000,
            1,
            0,
            senderNonce,
            null
        );
    }
}

package blockchain;

import org.hyperledger.besu.datatypes.Address;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;


public class GetBalance {
    private Address address;
    private String coin;
    private byte[] signature; // client signature
    private long balance;
    private int sequenceNumber; 

    public GetBalance(Address address, String coin, byte[] signature, long balance, int sequenceNumber) {
        this.address = address;
        this.coin = coin;
        this.signature = signature;
        this.balance = balance;
        this.sequenceNumber = sequenceNumber;
    }

    public Address getAddress() {
        return address;
    }

    public String getCoin() {
        return coin;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public long getBalance() {
        return balance;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public static String computeMappingStorageKey(String address) {
        String cleanAddress = address.startsWith("0x") ? address.substring(2) : address;

        // Left-pad address to 32 bytes (64 hex chars)
        String paddedAddress = String.format("%64s", cleanAddress).replace(' ', '0');

        // Left-pad slot 0 to 32 bytes (64 hex chars) - balances mapping is always at slot 0
        String paddedSlot = "0000000000000000000000000000000000000000000000000000000000000000";

        // Concatenate and hash with keccak256
        byte[] concatenated = Numeric.hexStringToByteArray(paddedAddress + paddedSlot);
        byte[] hash = Hash.sha3(concatenated);

        // Return with 0x prefix
        return Numeric.toHexString(hash);
    }
}

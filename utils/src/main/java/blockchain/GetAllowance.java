package blockchain;

import org.hyperledger.besu.datatypes.Address;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;


public class GetAllowance {
    private Address owner;
    private Address spender;
    private String coin;
    private byte[] signature;
    private long allowance;
    private int sequenceNumber;

    public GetAllowance(Address owner, Address spender, String coin, byte[] signature, long allowance, int sequenceNumber) {
        this.owner = owner;
        this.spender = spender;
        this.coin = coin;
        this.signature = signature;
        this.allowance = allowance;
        this.sequenceNumber = sequenceNumber;
    }

    public Address getOwner() {
        return owner;
    }

    public Address getSpender() {
        return spender;
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

    public long getAllowance() {
        return allowance;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public static String computeAllowanceMappingStorageKey(String ownerAddress, String spenderAddress) {
        String cleanOwner = ownerAddress.startsWith("0x") ? ownerAddress.substring(2) : ownerAddress;
        String cleanSpender = spenderAddress.startsWith("0x") ? spenderAddress.substring(2) : spenderAddress;

        // Left-pad owner address to 32 bytes (64 hex chars)
        String paddedOwner = String.format("%64s", cleanOwner).replace(' ', '0');

        // Slot 1 for allowances mapping (balances is slot 0)
        String paddedSlot = "0000000000000000000000000000000000000000000000000000000000000001";

        // First hash: owner + slot
        byte[] concatenated1 = Numeric.hexStringToByteArray(paddedOwner + paddedSlot);
        byte[] hash1 = Hash.sha3(concatenated1);

        // Left-pad spender address to 32 bytes (64 hex chars)
        String paddedSpender = String.format("%64s", cleanSpender).replace(' ', '0');

        // Second hash: spender + hash1
        String hash1Hex = Numeric.toHexStringNoPrefix(hash1);
        byte[] concatenated2 = Numeric.hexStringToByteArray(paddedSpender + hash1Hex);
        byte[] hash2 = Hash.sha3(concatenated2);

        // Return with 0x prefix
        return Numeric.toHexString(hash2);
    }
}

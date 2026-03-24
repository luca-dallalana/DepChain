package blockchain;

import org.hyperledger.besu.datatypes.Address;


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
}

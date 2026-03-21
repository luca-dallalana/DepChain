package blockchain;

public class Transaction {
    public String from;         // Sender address
    public String to;           // (null for contract deployment) either recipient address or contract address for calls
    public long value;          // DepCoin amount
    public byte[] data;         // empty for simple transfers(DepCoin), otherwise contains ABI-encoded function call
    public long gasLimit;       // Max gas to use
    public long gasPrice;       // Price per gas unit
    public long nonce_count;    // Sender nonce_count
    public byte[] signature;    // Transaction signature

    public Transaction(String from, String to, long value, byte[] data,
                      long gasLimit, long gasPrice, long nonce_count, byte[] signature) {
        this.from = from;
        this.to = to;
        this.value = value;
        this.data = data;
        this.gasLimit = gasLimit;
        this.gasPrice = gasPrice;
        this.nonce_count = nonce_count;
        this.signature = signature;
    }

    public Transaction() {}  

    public boolean isContractDeployment() {
        return to == null;
    }

    public long getMaxTransactionFee() {
        return gasPrice * gasLimit;
    }

    // Getters
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public long getValue() { return value; }
    public byte[] getData() { return data; }
    public long getGasLimit() { return gasLimit; }
    public long getGasPrice() { return gasPrice; }
    public long getNonce() { return nonce_count; }
    public byte[] getSignature() { return signature; }
}
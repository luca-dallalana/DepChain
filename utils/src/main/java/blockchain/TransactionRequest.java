package blockchain;

public class TransactionRequest {
    public int from;         // Sender address
    public int to;           // Recipient (null for contract deployment)
    public long value;          // ISTCoin amount
    public long gasLimit;       // Max gas to use
    public long gasPrice;       // Price per gas unit
    public long nonce_count;    // Sender nonce_count
    public byte[] signature;    // Transaction signature

    public TransactionRequest(int from, int to, long value,
                              long gasLimit, long gasPrice, long nonce_count, byte[] signature) {
        this.from = from;
        this.to = to;
        this.value = value;
        this.gasLimit = gasLimit;
        this.gasPrice = gasPrice;
        this.nonce_count = nonce_count;
        this.signature = signature;
    }
}

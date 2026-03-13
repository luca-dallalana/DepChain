package blockchain;

public abstract class Account {
    public String address;      // Hex string with 0x prefix
    public long balance;        // DepCoin balance
    public long nonce_count;    // Transaction count

    public Account(String address, long balance, long nonce_count) {
        this.address = address;
        this.balance = balance;
        this.nonce_count = nonce_count;
    }

    public Account() {}  // For GSON

    public abstract boolean isContract();
}

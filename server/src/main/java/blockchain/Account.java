package blockchain;

import java.util.HashMap;
import java.util.Map;

public class Account {
    public String address;                  // Hex string with 0x prefix
    public long balance;                    // DepCoin balance
    public long nonce_count;                // Transaction count (Client Request for EOA, Contract execution counter for Contract)
    public byte[] code;                     // Null for EOAs, EVM bytecode for contracts
    public Map<String, String> storage;     // Contract storage

    public Account(String address, long balance, long nonce_count) {
        this.address = address;
        this.balance = balance;
        this.nonce_count = nonce_count;
        this.code = null; // Default to null for EOAs, set for contracts
        this.storage = null; // Only used for contracts
    }

    public Account(String address, long balance, long nonce_count, byte[] code) {
        this.address = address;
        this.balance = balance;
        this.nonce_count = nonce_count;
        this.code = code; // Set for contracts
        this.storage = new HashMap<>();
    }

    public Account(String address, long balance, long nonce_count, byte[] code, Map<String, String> storage) {
        this.address = address;
        this.balance = balance;
        this.nonce_count = nonce_count;
        this.code = code;
        this.storage = storage != null ? storage : new HashMap<>();
    }

    public Account() {}  // For GSON

    public boolean isContract() {
        if(code == null || code.length == 0) {
            return false; // No code means it's an EOA
        }
        return true;
    }

    // Getters
    public String getAddress() { return address; }
    public long getBalance() { return balance; }
    public long getNonce() { return nonce_count; }
    public byte[] getCode() { return code; }
    public Map<String, String> getStorage() { return storage; }
}

package blockchain;

import java.util.HashMap;
import java.util.Map;

public class ContractAccount extends Account {
    public byte[] code;                    // EVM bytecode
    public Map<String, String> storage;    // Contract storage

    public ContractAccount(String address, long balance, long nonce_count,
                          byte[] code, Map<String, String> storage) {
        super(address, balance, nonce_count);
        this.code = code;
        this.storage = storage != null ? storage : new HashMap<>();
    }

    public ContractAccount() {
        super();
        this.storage = new HashMap<>();
    }

    @Override
    public boolean isContract() { return true; }
}

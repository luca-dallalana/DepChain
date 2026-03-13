package blockchain;

import java.util.HashMap;
import java.util.Map;

public class WorldState {
    public Map<String, Account> accounts;  // Address -> Account

    public WorldState(Map<String, Account> accounts) {
        this.accounts = accounts != null ? accounts : new HashMap<>();
    }

    public WorldState() {
        this.accounts = new HashMap<>();
    }

    // Helper methods
    public Account getAccount(String address) {
        return accounts.get(address);
    }

    public void putAccount(String address, Account account) {
        accounts.put(address, account);
    }

    public boolean hasAccount(String address) {
        return accounts.containsKey(address);
    }
}

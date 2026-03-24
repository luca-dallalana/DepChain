package blockchain;

import java.util.HashMap;
import java.util.Map;

import org.hyperledger.besu.datatypes.Address;

public class WorldState {
    public Map<Address, Account> accounts;  // Address -> Account

    public WorldState(Map<Address, Account> accounts) {
        this.accounts = accounts != null ? accounts : new HashMap<>();
    }

    public WorldState() {
        this.accounts = new HashMap<>();
    }

    // Helper methods
    public Account getAccount(Address address) {
        return accounts.get(address);
    }

    public void putAccount(Address address, Account account) {
        accounts.put(address, account);
    }

    public boolean hasAccount(Address address) {
        return accounts.containsKey(address);
    }
}

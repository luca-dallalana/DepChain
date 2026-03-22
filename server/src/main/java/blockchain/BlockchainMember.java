package blockchain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import blockchain.Transaction;
import blockchain.evm.EVMHelper;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;

public class BlockchainMember {

    // Consensus calls this when a block is decided
    public static void executeBlock(Block block){}

    // Consensus calls this to get the next block to propose
    public static Block buildBlock(List<Transaction> pendingTxs){
        List<Transaction> orderedTxs = orderTransactionsForBlock(pendingTxs);
        return new Block();
    }

    // Consensus calls this to validate a proposed block before voting
    public boolean isValidBlock(Block block){
        return true; // Implement block validation logic (e.g., check txs, state transitions)
    }

    public static List<Transaction> orderTransactionsForBlock(List<Transaction> transactions) {
        Map<String, Queue<Transaction>> bySender = new HashMap<>();

        for (Transaction tx : transactions) {
            bySender.computeIfAbsent(tx.getFrom().toHexString(), k -> new LinkedList<>()).add(tx);
        }

        for (Queue<Transaction> queue : bySender.values()) {
            ((LinkedList<Transaction>) queue).sort((a, b) -> Long.compare(a.getNonce(), b.getNonce()));
        }

        List<Transaction> ordered = new ArrayList<>();

        while (!bySender.isEmpty() && ordered.size() < 10) { //FIXME: limit block size to 10 transactions for simplicity
            String bestSender = null;
            long highestGasPrice = -1;

            for (Map.Entry<String, Queue<Transaction>> entry : bySender.entrySet()) {
                long gasPrice = entry.getValue().peek().getGasPrice();
                if (gasPrice > highestGasPrice) {
                    highestGasPrice = gasPrice;
                    bestSender = entry.getKey();
                }
            }

            if (bestSender != null) {
                Transaction tx = bySender.get(bestSender).poll();
                ordered.add(tx);

                if (bySender.get(bestSender).isEmpty()) {
                    bySender.remove(bestSender);
                }
            }
        }

        return ordered;
    }

    public static Map<String, String> extractStoragePublic(EVMHelper evm, Address contractAddress) {
        Map<String, String> storage = new HashMap<>();
        org.hyperledger.besu.evm.account.MutableAccount mutableAccount =
            (org.hyperledger.besu.evm.account.MutableAccount) evm.world.get(contractAddress);

        if (mutableAccount == null) {
            return storage;
        }

        Map<org.apache.tuweni.units.bigints.UInt256, org.apache.tuweni.units.bigints.UInt256> besuStorage =
            mutableAccount.getUpdatedStorage();

        for (Map.Entry<org.apache.tuweni.units.bigints.UInt256, org.apache.tuweni.units.bigints.UInt256> entry : besuStorage.entrySet()) {
            storage.put("0x" + entry.getKey().toHexString().substring(2),
                       "0x" + entry.getValue().toHexString().substring(2));
        }

        return storage;
    }

    private static void initializeEVM(EVMHelper evm, WorldState state) {
        for (Map.Entry<String, Account> entry : state.accounts.entrySet()) {
            String addrStr = entry.getKey();
            Account account = entry.getValue();
            Address addr = Address.fromHexString(addrStr);

            // Create account in EVM with balance
            evm.createAccount(addr, Wei.of(account.balance));

            // Get the mutable account to set additional properties
            org.hyperledger.besu.evm.account.MutableAccount besuAccount =
                (org.hyperledger.besu.evm.account.MutableAccount) evm.world.get(addr);

            if (besuAccount != null) {
                for (int i = 0; i < account.nonce_count; i++) {
                    besuAccount.incrementNonce();
                }

                // If contract account, load code and storage
                if (account.isContract()) {
                    // Set contract code
                    besuAccount.setCode(Bytes.wrap(account.code));

                    // Load contract storage
                    if (account.storage != null) {
                        for (Map.Entry<String, String> storageEntry : account.storage.entrySet()) {
                            org.apache.tuweni.units.bigints.UInt256 key =
                                org.apache.tuweni.units.bigints.UInt256.fromHexString(storageEntry.getKey());
                            org.apache.tuweni.units.bigints.UInt256 value =
                                org.apache.tuweni.units.bigints.UInt256.fromHexString(storageEntry.getValue());
                            besuAccount.setStorageValue(key, value);
                        }
                    }
                }
            }
        }
    }

    private static WorldState extractState(EVMHelper evm, Set<String> trackedAddresses) {
        WorldState finalState = new WorldState();

        for (String addrStr : trackedAddresses) {
            Address addr = Address.fromHexString(addrStr);
            org.hyperledger.besu.evm.account.MutableAccount besuAccount =
                (org.hyperledger.besu.evm.account.MutableAccount) evm.world.get(addr);

            if (besuAccount == null) {
                continue;
            }

            long balance = besuAccount.getBalance().toLong();
            long nonce = besuAccount.getNonce();

            if (besuAccount.getCode() == null || besuAccount.getCode().isEmpty()) {
                // EOA (Externally Owned Account)
                finalState.putAccount(addrStr, new Account(addrStr, balance, nonce));
            } else {
                // Contract Account
                byte[] code = besuAccount.getCode().toArray();
                Map<String, String> storage = extractStoragePublic(evm, addr);
                finalState.putAccount(addrStr, new Account(addrStr, balance, nonce, code, storage));
            }
        }

        return finalState;
    }

    public static WorldState computeState(EVMHelper evm, List<Transaction> transactions, WorldState initialState) {
        Set<String> trackedAddresses = new HashSet<>();

        // Step 1: Initialize EVM with previous state
        initializeEVM(evm, initialState);

        // Track all existing addresses from initial state
        trackedAddresses.addAll(initialState.accounts.keySet());

        // Step 2: Execute each transaction 
        for (Transaction tx : transactions) {
            if (tx.to == null) {
                // Contract deployment not supported in computeState (only in genesis setup)
                throw new RuntimeException("Contract deployment must be done in genesis setup, not via computeState()");
            }

            // Contract call
            Address caller = tx.from;
            Address contractAddress = tx.to;
            Bytes callData = Bytes.wrap(tx.data);

            // Execute the contract call
            EVMHelper.ExecutionResult result = evm.executeCall(caller, contractAddress, callData);

            if (!result.isSuccess()) {
                throw new RuntimeException("Transaction failed: " + tx.toString());
            }

            // Track any new addresses that might have been created (recipient accounts, etc.)
            trackedAddresses.add(caller.toHexString());
            trackedAddresses.add(contractAddress.toHexString());

            // Note: Gas fees and signature verification will be added later
            // Sender nonce is updated automatically by EVM
        }

        // Step 3: Extract final state from EVM
        return extractState(evm, trackedAddresses);
    }
}
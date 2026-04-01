package blockchain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.apache.tuweni.units.bigints.UInt256;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.MutableAccount;

import blockchain.evm.EVMHelper;


public class BlockchainMember {

    private static final long BLOCK_GAS_LIMIT = 210000;
    private static final long BLOCK_BUILD_TIMEOUT_MS = 5000;
    private static final long BUILD_POLL_INTERVAL_MS = 100;

    // Consensus calls this when a block is decided
    public static Block executeBlock(Block block, BlockStore blockStore, Block lastExecutedBlock) {
        if (block == null) throw new IllegalArgumentException("Cannot execute null block");
        if (blockStore == null) throw new IllegalArgumentException("BlockStore cannot be null");
        if (lastExecutedBlock == null) throw new IllegalArgumentException("Last executed block cannot be null");

        List<Block> blocksToExecute = blockStore.getBlocksUntil(block.blockHash, lastExecutedBlock.blockHash);

        if (blocksToExecute.isEmpty()) {
            System.out.println("Block #" + block.blockNumber + " already executed");
            return lastExecutedBlock;
        }

        Collections.reverse(blocksToExecute);

        Block currentBlock = lastExecutedBlock;
        for (Block b : blocksToExecute) {
            try {
                String blockPath = "../blockchain_data/block_" + b.blockNumber + ".json";
                b.saveToFile(blockPath);
                System.out.println("Executed and persisted block #" + b.blockNumber + " with hash: " + b.blockHash);
            } catch (Exception e) {
                System.err.println("Failed to persist block #" + b.blockNumber + ": " + e.getMessage());
            }
            currentBlock = b;
        }

        return currentBlock;
    }

    // Consensus calls this to get the next block to propose
    public static Block buildBlock(Block parent, List<Transaction> pendingTxs) throws Exception {
        List<Transaction> orderedTxs = orderTransactionsForBlock(pendingTxs);
        EVMHelper evm = new EVMHelper();
        WorldState newState = computeState(evm, orderedTxs, parent.state);
        return Block.createLeaf(parent, orderedTxs, newState);
    }

    // Leader path: wait up to timeout, but stop early when block gas cap is reached.
    public static Block buildBlockForProposal(Block parent, List<Transaction> pendingTxs) throws Exception {
        List<Transaction> orderedTxs = waitAndOrderTransactionsForBlock(pendingTxs);
        EVMHelper evm = new EVMHelper();
        WorldState newState = computeState(evm, orderedTxs, parent.state);
        return Block.createLeaf(parent, orderedTxs, newState);
    }

    // Consensus calls this to validate a proposed block before voting
    public static boolean isValidBlock(Block block, Block parent) {
        try {
            if (parent == null) return false;
            if (!block.parentBlockHash.equals(parent.depHash())) return false;
            if (block.blockNumber != parent.blockNumber + 1) return false;

            EVMHelper evm = new EVMHelper();
            WorldState computedState = computeState(evm, block.transactions, parent.state);

            if (!statesEqual(computedState, block.state)) return false;

            String computedHash = block.depHash();
            if (!computedHash.equals(block.blockHash)) return false;

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean statesEqual(WorldState s1, WorldState s2) {
        if (s1.accounts.size() != s2.accounts.size()) return false;
        for (Address addr : s1.accounts.keySet()) {
            Account a1 = s1.accounts.get(addr);
            Account a2 = s2.accounts.get(addr);
            if (a2 == null) return false;
            if (a1.balance != a2.balance) return false;
            if (a1.nonce_count != a2.nonce_count) return false;

            if (!java.util.Arrays.equals(a1.code, a2.code)) return false;

            if (a1.storage == null && a2.storage != null) return false;
            if (a1.storage != null && a2.storage == null) return false;
            if (a1.storage != null) {
                if (a1.storage.size() != a2.storage.size()) return false;
                for (String key : a1.storage.keySet()) {
                    if (!a1.storage.get(key).equals(a2.storage.get(key))) return false;
                }
            }
        }
        return true;
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
        long totalGas = 0;

        while (!bySender.isEmpty()) {
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
                Transaction tx = bySender.get(bestSender).peek();
                long txGas = tx.getGasLimit();

                if (!ordered.isEmpty() && totalGas + txGas > BLOCK_GAS_LIMIT) {
                    break;
                }

                tx = bySender.get(bestSender).poll();
                ordered.add(tx);
                totalGas += txGas;

                if (bySender.get(bestSender).isEmpty()) {
                    bySender.remove(bestSender);
                }

                if (totalGas >= BLOCK_GAS_LIMIT) {
                    break;
                }
            }
        }

        return ordered;
    }

    private static List<Transaction> waitAndOrderTransactionsForBlock(List<Transaction> pendingTxs) {
        long deadline = System.currentTimeMillis() + BLOCK_BUILD_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            List<Transaction> candidate = orderTransactionsForBlock(new ArrayList<>(pendingTxs));
            if (totalGas(candidate) >= BLOCK_GAS_LIMIT) {
                return candidate;
            }

            try {
                Thread.sleep(BUILD_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return orderTransactionsForBlock(new ArrayList<>(pendingTxs));
    }

    private static long totalGas(List<Transaction> transactions) {
        long total = 0;
        for (Transaction tx : transactions) {
            total += tx.getGasLimit();
        }
        return total;
    }

    public static Map<String, String> extractStoragePublic(EVMHelper evm, Address contractAddress) {
        Map<String, String> storage = new HashMap<>();
        MutableAccount mutableAccount = (MutableAccount) evm.world.get(contractAddress);

        if (mutableAccount == null) {
            return storage;
        }

        Map<UInt256, UInt256> besuStorage = mutableAccount.getUpdatedStorage();

        for (Map.Entry<UInt256, UInt256> entry : besuStorage.entrySet()) {
            storage.put("0x" + entry.getKey().toHexString().substring(2),
                       "0x" + entry.getValue().toHexString().substring(2));
        }

        return storage;
    }

    private static void initializeEVM(EVMHelper evm, WorldState state) {
        for (Map.Entry<Address, Account> entry : state.accounts.entrySet()) {
            Address addr = entry.getKey();
            Account account = entry.getValue();

            // Create account in EVM with balance
            evm.createAccount(addr, Wei.of(account.balance));

            // Get the mutable account to set additional properties
            MutableAccount besuAccount = (MutableAccount) evm.world.get(addr);

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
                            UInt256 key = UInt256.fromHexString(storageEntry.getKey());
                            UInt256 value = UInt256.fromHexString(storageEntry.getValue());
                            besuAccount.setStorageValue(key, value);
                        }
                    }
                }
            }
        }
    }

    private static WorldState extractState(EVMHelper evm, Set<Address> trackedAddresses) {
        WorldState finalState = new WorldState();

        for (Address addr : trackedAddresses) {
            MutableAccount besuAccount = (MutableAccount) evm.world.get(addr);

            if (besuAccount == null) {
                continue;
            }

            long balance = besuAccount.getBalance().toLong();
            long nonce = besuAccount.getNonce();

            if (besuAccount.getCode() == null || besuAccount.getCode().isEmpty()) {
                // EOA (Externally Owned Account)
                finalState.putAccount(addr, new Account(balance, nonce));
            } else {
                // Contract Account
                byte[] code = besuAccount.getCode().toArray();
                Map<String, String> storage = extractStoragePublic(evm, addr);
                finalState.putAccount(addr, new Account(balance, nonce, code, storage));
            }
        }

        return finalState;
    }

    public static WorldState computeState(EVMHelper evm, List<Transaction> transactions, WorldState initialState) {
        Set<Address> trackedAddresses = new HashSet<>();

        // Step 1: Initialize EVM with previous state
        initializeEVM(evm, initialState);

        // Track all existing addresses from initial state
        trackedAddresses.addAll(initialState.accounts.keySet());

        // Step 2: Execute each transaction
        for (Transaction tx : transactions) {
            try {
                if (tx.to == null) throw new RuntimeException("Contract deployment must be done in genesis setup, not via computeState()");

                MutableAccount senderAccount = (MutableAccount) evm.world.get(tx.from);
                if (senderAccount == null) throw new RuntimeException("Sender account does not exist");
                if (tx.getGasPrice() <= 0 || tx.getGasLimit() <= 0) throw new RuntimeException("Gas price and limit must be positive");
                if (senderAccount.getNonce() != tx.getNonce()) throw new RuntimeException("Invalid nonce");

                // Increment nonce immediately after validation - any transaction with valid nonce increments it
                senderAccount.incrementNonce();
                trackedAddresses.add(tx.from);

                long maxCost = tx.getValue() + (tx.getGasPrice() * tx.getGasLimit());
                if (senderAccount.getBalance().toLong() < maxCost) throw new RuntimeException("Insufficient balance");

                boolean isNativeTransfer = (tx.getData() == null || tx.getData().length == 0);
                long gasUsed;

                if (isNativeTransfer) {
                    gasUsed = 21000; // Fixed gas cost for native transfer FIXME: talvez mudar

                    if (gasUsed > tx.getMaxTransactionFee()) {
                        senderAccount.setBalance(senderAccount.getBalance().subtract(Wei.of(tx.getGasPrice() * tx.getGasLimit())));
                        System.out.println("Native transfer failed due to insufficient gas limit");
                        tx.executionSuccess = false;
                        continue;
                    }

                    MutableAccount recipientAccount = (MutableAccount) evm.world.get(tx.to);
                    if (recipientAccount == null) {
                        evm.createAccount(tx.to, Wei.ZERO);
                        recipientAccount = (MutableAccount) evm.world.get(tx.to);
                        trackedAddresses.add(tx.to);
                    }

                    if (tx.getValue() > 0) {
                        senderAccount.setBalance(senderAccount.getBalance().subtract(Wei.of(tx.getValue())));
                        recipientAccount.setBalance(recipientAccount.getBalance().add(Wei.of(tx.getValue())));
                    }

                    tx.executionSuccess = true;

                } else {
                    EVMHelper.ExecutionResult result = evm.executeCall(tx.from, tx.to, Bytes.wrap(tx.getData()));
                    gasUsed = result.getGasUsed();

                    if (gasUsed > tx.getGasLimit()) {
                        senderAccount.setBalance(senderAccount.getBalance().subtract(Wei.of(tx.getGasPrice() * tx.getGasLimit())));
                        System.out.println("Transaction failed due to lack of gas");
                        tx.executionSuccess = false;
                        continue;
                    }

                    if (!result.isSuccess()) {
                        senderAccount.setBalance(senderAccount.getBalance().subtract(Wei.of(tx.getGasPrice() * gasUsed)));
                        System.out.println("Contract call failed");
                        tx.executionSuccess = false;
                        continue;
                    }
                    tx.executionSuccess = true;
                }

                senderAccount.setBalance(senderAccount.getBalance().subtract(Wei.of(tx.getGasPrice() * gasUsed)));
                trackedAddresses.add(tx.from);
                trackedAddresses.add(tx.to);
            } catch (RuntimeException e) {
                System.err.println("Transaction validation failed: " + e.getMessage() + " (nonce=" + tx.nonce_count + ", from=" + tx.from + ")");
                tx.executionSuccess = false;
            }
        }

        // Step 3: Extract final state from EVM
        return extractState(evm, trackedAddresses);
    }
}
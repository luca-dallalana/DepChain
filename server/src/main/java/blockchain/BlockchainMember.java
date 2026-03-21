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
    public void executeBlock(Block block){}

    // Consensus calls this to get the next block to propose
    public Block buildBlock(List<Transaction> pendingTxs){
        List<Transaction> orderedTxs = orderTransactionsForBlock(pendingTxs);
        return new Block();
    }

    // Consensus calls this to validate a proposed block before voting
    public boolean isValidBlock(Block block){
        return true; // Implement block validation logic (e.g., check txs, state transitions)
    }

    public static Transaction createTransaction(TransactionRequest request) {
        // from = getaddress(request.from)
        // to = getaddress(request.to)
        return new Transaction();
    }

    public List<Transaction> orderTransactionsForBlock(List<Transaction> transactions) {
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

    public static WorldState executeBlock(EVMHelper evm, List<Transaction> transactions, WorldState initialState) {
        Set<String> trackedAddresses = new HashSet<>();

        // Track existing accounts from initialState (already loaded in EVM)
        for (String address : initialState.accounts.keySet()) {
            trackedAddresses.add(address);
        }

        // Execute each transaction
        for (int i = 0; i < transactions.size(); i++) {
            Transaction tx = transactions.get(i);

            if (tx.to == null) {
                // Contract deployment (genesis only - AccessControl at index 0, ISTCoin at index 1)
                Address deployer = tx.from;

                // Determine contract address based on transaction index (genesis only)
                Address contractAddr;
                if (i == 0) {
                    contractAddr = Address.fromHexString(Block.ACCESS_CONTROL_ADDRESS);
                } else if (i == 1) {
                    contractAddr = Address.fromHexString(Block.IST_COIN_ADDRESS);
                } else {
                    throw new RuntimeException("Only genesis contracts (AccessControl, ISTCoin) are supported. Transaction index: " + i);
                }

                Bytes deploymentCode = Bytes.wrap(tx.data);

                boolean deployed = evm.deployContract(deployer, contractAddr, deploymentCode);
                if (!deployed) {
                    throw new RuntimeException("Failed to deploy contract at " + contractAddr.toHexString());
                }

                trackedAddresses.add(contractAddr.toHexString());
            } else {
                // Contract call (not needed for genesis, implement later for regular blocks)
                throw new RuntimeException("Contract calls not yet implemented in executeBlock()");
            }

            // Note: Gas fees and signature verification will be added later
            // For genesis: gasPrice = 0 (no fees), signature = null (trusted)
        }

        // Extract final state from EVM
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
}
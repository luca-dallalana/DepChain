package blockchain;

import blockchain.evm.ABIEncoder;
import blockchain.evm.EVMHelper;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import blockchain.AddressUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Block {
    public static final String ACCESS_CONTROL_ADDRESS = "0x1234567891234567891234567891234567891234";
    public static final String IST_COIN_ADDRESS = "0x5555555555555555555555555555555555555555";

    public String blockHash;            // Block hash (hex)
    public String parentBlockHash;    // Previous block (null for genesis)
    public List<Transaction> transactions;
    public WorldState state;            // World state after executing txs
    public long timestamp;
    public long blockNumber;            // Height in blockchain

    public Block(String blockHash, String parentBlockHash,
                List<Transaction> transactions, WorldState state,
                long timestamp, long blockNumber) {
        this.blockHash = blockHash;
        this.parentBlockHash = parentBlockHash;
        this.transactions = transactions != null ? transactions : new ArrayList<>();
        this.state = state;
        this.timestamp = timestamp;
        this.blockNumber = blockNumber;
    }

    public Block() {
        this.transactions = new ArrayList<>();
        this.state = new WorldState();
    }

    public boolean isGenesisBlock() {
        return blockNumber == 0 && parentBlockHash == null;
    }

    public static Block createAndSaveGenesis(String projectRoot) {
        try {
            // 1. Generate client addresses from public keys
            String client0Addr = AddressUtils.generateAddressFromPublicKey(projectRoot + "/rsa_keys/client_0/client_0.pubkey");
            String client1Addr = AddressUtils.generateAddressFromPublicKey(projectRoot + "/rsa_keys/client_1/client_1.pubkey");

            // 2. Create EVM and initialize with member EOA accounts (static membership)
            EVMHelper evm = new EVMHelper();
            evm.createAccount(Address.fromHexString(client0Addr), Wei.of(100000));
            evm.createAccount(Address.fromHexString(client1Addr), Wei.of(100000));

            // 3. Create initial state with member EOAs (before contract deployment)
            WorldState initialState = new WorldState();
            initialState.putAccount(client0Addr, new Account(client0Addr, 100000, 0));
            initialState.putAccount(client1Addr, new Account(client1Addr, 100000, 0));

            // 4. Load contract bytecode
            String accessControlBytecode = BytecodeLoader.loadBytecode("AccessControl");
            String istCoinBytecode = BytecodeLoader.loadBytecode("ISTCoin");

            // 5. Create deployment transactions
            List<Transaction> transactions = new ArrayList<>();

            // AccessControl deployment transaction
            Address acAddress = Address.fromHexString(ACCESS_CONTROL_ADDRESS);
            Bytes acConstructorParams = ABIEncoder.encodeAccessControlConstructor(
                new Address[]{Address.fromHexString(client0Addr), Address.fromHexString(client1Addr)}
            );
            Bytes acDeploymentCode = Bytes.concatenate(
                Bytes.fromHexString(accessControlBytecode),
                acConstructorParams
            );
            Transaction acDeployTx = new Transaction(
                Address.fromHexString(client0Addr),
                null,  // Contract deployment
                0,     // No value transfer
                acDeploymentCode.toArray(),
                10000000,  // Gas limit
                0,         // Gas price (free for genesis)
                0,         // Nonce
                null       // No signature (trusted genesis)
            );
            transactions.add(acDeployTx);

            // ISTCoin deployment transaction
            Address istAddress = Address.fromHexString(IST_COIN_ADDRESS);
            Bytes istConstructorParams = ABIEncoder.encodeISTCoinConstructor(acAddress, Address.fromHexString(client0Addr));
            Bytes istDeploymentCode = Bytes.concatenate(
                Bytes.fromHexString(istCoinBytecode),
                istConstructorParams
            );
            Transaction istDeployTx = new Transaction(
                Address.fromHexString(client0Addr),
                null,  // Contract deployment
                0,     // No value transfer
                istDeploymentCode.toArray(),
                10000000,  // Gas limit
                0,         // Gas price (free for genesis)
                1,         // Nonce
                null       // No signature (trusted genesis)
            );
            transactions.add(istDeployTx);

            // 6. Execute block to get final state (transactions drive execution)
            WorldState finalState = BlockchainMember.executeBlock(evm, transactions, initialState);

            System.out.println("AccessControl deployed at: " + ACCESS_CONTROL_ADDRESS);
            System.out.println("ISTCoin deployed at: " + IST_COIN_ADDRESS);
            System.out.println("Genesis state contains " + finalState.accounts.size() + " accounts");

            // 7. Create genesis block (without hash initially)
            Block genesis = new Block(
                null,          // blockHash - compute later
                null,          // parentBlockHash
                transactions,
                finalState,
                0,             // timestamp
                0              // blockNumber
            );

            // 8. Compute block hash
            String blockJson = genesis.toJson();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(blockJson.getBytes(StandardCharsets.UTF_8));
            genesis.blockHash = "0x" + AddressUtils.bytesToHex(hash);

            System.out.println("Genesis block hash: " + genesis.blockHash);

            // 9. Save to file (persistence)
            String genesisPath = projectRoot + "/blockchain_data/genesis_block.json";
            genesis.saveToFile(genesisPath);
            System.out.println("Genesis block saved to: " + genesisPath);

            return genesis;

        } catch (Exception e) {
            throw new RuntimeException("Failed to create genesis block", e);
        }
    }


    public String toJson() {
        return network.GsonUtils.GSON.toJson(this);
    }

    public void saveToFile(String filePath) throws IOException {
        java.nio.file.Path path = Paths.get(filePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, toJson());
    }
}

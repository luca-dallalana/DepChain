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
import java.util.Set;

public class Block {
    public static final String ACCESS_CONTROL_ADDRESS = "0x1234567891234567891234567891234567891234";
    public static final String IST_COIN_ADDRESS = "0x5555555555555555555555555555555555555555";
    public static final String ADMIN_ADDRESS = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

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
            Address client0HexAddress = Address.fromHexString(client0Addr);
            Address client1HexAddress = Address.fromHexString(client1Addr);

            // 2. Create EVM and initialize accounts
            EVMHelper evm = new EVMHelper();

            // Admin account for contract deployment (keeps client nonces clean)
            Address adminAddress = Address.fromHexString(ADMIN_ADDRESS);
            evm.createAccount(adminAddress, Wei.of(1000000));

            // Member EOA accounts (static membership)
            evm.createAccount(client0HexAddress, Wei.of(100000));
            evm.createAccount(client1HexAddress, Wei.of(100000));

            // 3. Load contract bytecode
            String accessControlBytecode = BytecodeLoader.loadBytecode("AccessControl");
            String istCoinBytecode = BytecodeLoader.loadBytecode("ISTCoin");

            // 4. Deploy contracts manually (genesis-only setup, admin deploys)
            Address acAddress = Address.fromHexString(ACCESS_CONTROL_ADDRESS);
            Bytes acConstructorParams = ABIEncoder.encodeAccessControlConstructor(
                new Address[]{client0HexAddress, client1HexAddress}
            );
            Bytes acDeploymentCode = Bytes.concatenate(
                Bytes.fromHexString(accessControlBytecode),
                acConstructorParams
            );
            boolean acDeployed = evm.deployContract(
                adminAddress,
                acAddress,
                acDeploymentCode
            );
            if (!acDeployed) {
                throw new RuntimeException("Failed to deploy AccessControl contract");
            }

            Address istAddress = Address.fromHexString(IST_COIN_ADDRESS);
            Bytes istConstructorParams = ABIEncoder.encodeISTCoinConstructor(acAddress, adminAddress);
            Bytes istDeploymentCode = Bytes.concatenate(
                Bytes.fromHexString(istCoinBytecode),
                istConstructorParams
            );
            boolean istDeployed = evm.deployContract(
                adminAddress,
                istAddress,
                istDeploymentCode
            );
            if (!istDeployed) {
                throw new RuntimeException("Failed to deploy ISTCoin contract");
            }

            // 5. Create deployment transactions (for record-keeping in genesis block)
            List<Transaction> transactions = new ArrayList<>();
            transactions.add(new Transaction(
                adminAddress,
                null,  // Contract deployment
                0,     // No value transfer
                acDeploymentCode.toArray(),
                10000000,  // Gas limit
                0,         // Gas price (free for genesis)
                0,         // Nonce
                null       // No signature (trusted genesis)
            ));
            transactions.add(new Transaction(
                adminAddress,
                null,  // Contract deployment
                0,     // No value transfer
                istDeploymentCode.toArray(),
                10000000,  // Gas limit
                0,         // Gas price (free for genesis)
                1,         // Nonce
                null       // No signature (trusted genesis)
            ));

            // 6. Extract final state from EVM after manual deployment
            WorldState finalState = new WorldState();
            Set<String> trackedAddresses = Set.of(
                ADMIN_ADDRESS,
                client0Addr,
                client1Addr,
                ACCESS_CONTROL_ADDRESS,
                IST_COIN_ADDRESS
            );

            for (String addrStr : trackedAddresses) {
                Address addr = Address.fromHexString(addrStr);
                org.hyperledger.besu.evm.account.MutableAccount besuAccount =
                    (org.hyperledger.besu.evm.account.MutableAccount) evm.world.get(addr);

                if (besuAccount == null) continue;

                long balance = besuAccount.getBalance().toLong();
                long nonce = besuAccount.getNonce();

                if (besuAccount.getCode() == null || besuAccount.getCode().isEmpty()) {
                    // EOA
                    finalState.putAccount(addrStr, new Account(addrStr, balance, nonce));
                } else {
                    // Contract
                    byte[] code = besuAccount.getCode().toArray();
                    Map<String, String> storage = BlockchainMember.extractStoragePublic(evm, addr);
                    finalState.putAccount(addrStr, new Account(addrStr, balance, nonce, code, storage));
                }
            }

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

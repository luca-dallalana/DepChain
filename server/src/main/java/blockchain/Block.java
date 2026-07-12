package blockchain;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.MutableAccount;

import blockchain.evm.ABIEncoder;
import blockchain.evm.EVMHelper;
import crypto.CryptoLib;

public class Block {
    public static final String ACCESS_CONTROL_ADDRESS = "0x1234567891234567891234567891234567891234";
    public static final String IST_COIN_ADDRESS = "0x5555555555555555555555555555555555555555";
    public static final String DEP_TOKEN_ADDRESS = "0x6666666666666666666666666666666666666666";
    public static final String SLASHING_CONTRACT_ADDRESS = "0x7777777777777777777777777777777777777777";
    public static final String AMM_ADDRESS = "0x8888888888888888888888888888888888888888";
    public static final String ORACLE_ADDRESS = "0x9999999999999999999999999999999999999999";
    public static final String ADMIN_ADDRESS = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    public String blockHash;
    public String parentBlockHash;
    public List<Transaction> transactions;
    public WorldState state;
    public long blockNumber;
    public long baseFeePerGas;
    public long totalGasUsed;

    public Block(String blockHash, String parentBlockHash,
                List<Transaction> transactions, WorldState state,
                long blockNumber, long baseFeePerGas, long totalGasUsed) {
        this.blockHash = blockHash;
        this.parentBlockHash = parentBlockHash;
        this.transactions = transactions != null ? transactions : new ArrayList<>();
        this.state = state;
        this.blockNumber = blockNumber;
        this.baseFeePerGas = baseFeePerGas;
        this.totalGasUsed = totalGasUsed;
    }

    public Block() {
        this.transactions = new ArrayList<>();
        this.state = new WorldState();
        this.baseFeePerGas = 1;
        this.totalGasUsed = 0;
    }

    public boolean isGenesisBlock() {
        return blockNumber == 0 && parentBlockHash == null;
    }

    public String depHash() throws Exception {
        if (blockHash != null && !blockHash.isBlank()) {
            return blockHash;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeString(baos, parentBlockHash);
        writeLong(baos, blockNumber);

        if (transactions != null) {
            for (Transaction tx : transactions) {
                writeInt(baos, tx.senderPort);
                writeString(baos, tx.from != null ? tx.from.toHexString() : null);
                writeString(baos, tx.to != null ? tx.to.toHexString() : null);
                writeLong(baos, tx.value);
                writeLong(baos, tx.gasLimit);
                writeLong(baos, tx.maxFeePerGas);
                writeLong(baos, tx.maxPriorityFeePerGas);
                writeInt(baos, tx.nonce_count);
                writeBytes(baos, tx.data);
                writeBytes(baos, tx.signature);
            }
        }

        writeWorldState(baos, state);
        writeLong(baos, baseFeePerGas);
        writeLong(baos, totalGasUsed);

        byte[] hash = CryptoLib.hash(baos.toByteArray());
        return "0x" + AddressUtils.bytesToHex(hash);
    }

    public static Block createLeaf(Block parent, List<Transaction> transactions, WorldState state,
                                   long baseFeePerGas, long totalGasUsed) throws Exception {
        if (parent == null) {
            throw new IllegalArgumentException("Parent block cannot be null");
        }

        Block leaf = new Block(
            null,
            parent.blockHash,
            transactions,
            state,
            parent.blockNumber + 1,
            baseFeePerGas,
            totalGasUsed
        );
        leaf.blockHash = leaf.depHash();
        return leaf;
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
            evm.createAccount(client0HexAddress, Wei.of(1000000));
            evm.createAccount(client1HexAddress, Wei.of(1000000));

            // 3. Load contract bytecodes
            String istCoinBytecode = BytecodeLoader.loadBytecode("ISTCoin");
            String slashingBytecode = BytecodeLoader.loadBytecode("SlashingContract");
            String depTokenBytecode = BytecodeLoader.loadBytecode("DepToken");
            String ammBytecode = BytecodeLoader.loadBytecode("AMM");
            String oracleBytecode = BytecodeLoader.loadBytecode("PriceOracle");

            // 4. Deploy ISTCoin contract manually (genesis-only setup, admin deploys)
            Address istAddress = Address.fromHexString(IST_COIN_ADDRESS);
            Bytes istConstructorParams = ABIEncoder.encodeISTCoinConstructor(client0HexAddress, client1HexAddress);
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

            // Deploy SlashingContract
            Address slashingAddress = Address.fromHexString(SLASHING_CONTRACT_ADDRESS);
            Bytes slashingDeploymentCode = Bytes.fromHexString(slashingBytecode);
            boolean slashingDeployed = evm.deployContract(
                adminAddress,
                slashingAddress,
                slashingDeploymentCode
            );
            if (!slashingDeployed) {
                throw new RuntimeException("Failed to deploy SlashingContract");
            }

            // Deploy DepToken
            Address depTokenAddress = Address.fromHexString(DEP_TOKEN_ADDRESS);
            Bytes depTokenConstructorParams = ABIEncoder.encodeDepTokenConstructor(client0HexAddress, client1HexAddress);
            Bytes depTokenDeploymentCode = Bytes.concatenate(
                Bytes.fromHexString(depTokenBytecode),
                depTokenConstructorParams
            );
            boolean depTokenDeployed = evm.deployContract(
                adminAddress,
                depTokenAddress,
                depTokenDeploymentCode
            );
            if (!depTokenDeployed) {
                throw new RuntimeException("Failed to deploy DepToken contract");
            }

            // Deploy AMM
            Address ammContractAddress = Address.fromHexString(AMM_ADDRESS);
            Bytes ammConstructorParams = ABIEncoder.encodeAMMConstructor(istAddress, depTokenAddress);
            Bytes ammDeploymentCode = Bytes.concatenate(
                Bytes.fromHexString(ammBytecode),
                ammConstructorParams
            );
            boolean ammDeployed = evm.deployContract(
                adminAddress,
                ammContractAddress,
                ammDeploymentCode
            );
            if (!ammDeployed) {
                throw new RuntimeException("Failed to deploy AMM contract");
            }

            // Deploy PriceOracle
            Address oracleContractAddress = Address.fromHexString(ORACLE_ADDRESS);
            Bytes oracleConstructorParams = ABIEncoder.encodeOracleConstructor(adminAddress);
            Bytes oracleDeploymentCode = Bytes.concatenate(
                Bytes.fromHexString(oracleBytecode),
                oracleConstructorParams
            );
            boolean oracleDeployed = evm.deployContract(
                adminAddress,
                oracleContractAddress,
                oracleDeploymentCode
            );
            if (!oracleDeployed) {
                throw new RuntimeException("Failed to deploy PriceOracle contract");
            }

            // 5. Create deployment transactions (for record-keeping in genesis block)
            List<Transaction> transactions = new ArrayList<>();
            transactions.add(new Transaction(
                -1, adminAddress, null, 0, istDeploymentCode.toArray(),
                10000000, 0, 0, 0, null
            ));
            transactions.add(new Transaction(
                -1, adminAddress, null, 0, slashingDeploymentCode.toArray(),
                10000000, 0, 0, 0, null
            ));
            transactions.add(new Transaction(
                -1, adminAddress, null, 0, depTokenDeploymentCode.toArray(),
                10000000, 0, 0, 0, null
            ));
            transactions.add(new Transaction(
                -1, adminAddress, null, 0, ammDeploymentCode.toArray(),
                10000000, 0, 0, 0, null
            ));
            transactions.add(new Transaction(
                -1, adminAddress, null, 0, oracleDeploymentCode.toArray(),
                10000000, 0, 0, 0, null
            ));

            // 6. Extract final state from EVM after manual deployments
            WorldState finalState = new WorldState();
            Set<String> trackedAddresses = new HashSet<>(Arrays.asList(
                ADMIN_ADDRESS,
                client0Addr,
                client1Addr,
                IST_COIN_ADDRESS,
                SLASHING_CONTRACT_ADDRESS,
                DEP_TOKEN_ADDRESS,
                AMM_ADDRESS,
                ORACLE_ADDRESS
            ));

            for (String addrStr : trackedAddresses) {
                Address addr = Address.fromHexString(addrStr);
                MutableAccount besuAccount = (MutableAccount) evm.world.get(addr);

                if (besuAccount == null) continue;

                long balance = besuAccount.getBalance().toLong();
                long nonce = besuAccount.getNonce();

                if (besuAccount.getCode() == null || besuAccount.getCode().isEmpty()) {
                    // EOA
                    finalState.putAccount(addr, new Account(balance, nonce));
                } else {
                    // Contract
                    byte[] code = besuAccount.getCode().toArray();
                    Map<String, String> storage = BlockchainMember.extractStoragePublic(evm, addr);
                    finalState.putAccount(addr, new Account(balance, nonce, code, storage));
                }
            }

            System.out.println("ISTCoin deployed at: " + IST_COIN_ADDRESS);
            System.out.println("SlashingContract deployed at: " + SLASHING_CONTRACT_ADDRESS);
            System.out.println("DepToken deployed at: " + DEP_TOKEN_ADDRESS);
            System.out.println("AMM deployed at: " + AMM_ADDRESS);
            System.out.println("PriceOracle deployed at: " + ORACLE_ADDRESS);
            System.out.println("Genesis state contains " + finalState.accounts.size() + " accounts");

            // 7. Create genesis block (without hash initially)
            Block genesis = new Block(
                null,
                null,
                transactions,
                finalState,
                0,
                1,  // baseFeePerGas
                0   // totalGasUsed
            );

            // 8. Compute block hash
            genesis.blockHash = genesis.depHash();

            System.out.println("Genesis block hash: " + genesis.blockHash);

            // 9. Clean the directory (remove old genesis and blocks if any)
            String blockchainPath = projectRoot + "/blockchain_data";
            try {
                if (Files.exists(Paths.get(blockchainPath))) {
                    deleteDirectory(Paths.get(blockchainPath));
                }
            } catch (IOException e) {
                System.err.println("Error deleting existing blockchain files: " + e.getMessage());
            }

            // 10. Save to file (persistence)
            String genesisPath = projectRoot + "/blockchain_data/genesis_block.json";
            genesis.saveToFile(genesisPath);
            System.out.println("Genesis block saved to: " + genesisPath);

            return genesis;

        } catch (Exception e) {
            throw new RuntimeException("Failed to create genesis block", e);
        }
    }

    public static void deleteDirectory(Path path) throws IOException {
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
    }

    public String toJson() {
        return network.GsonUtils.PRETTY_GSON.toJson(this);
    }

    public void saveToFile(String filePath) throws IOException {
        java.nio.file.Path path = Paths.get(filePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, toJson());
    }

    private static void writeInt(ByteArrayOutputStream baos, int value) {
        byte[] bytes = ByteBuffer.allocate(4).putInt(value).array();
        baos.write(bytes, 0, bytes.length);
    }

    private static void writeLong(ByteArrayOutputStream baos, long value) {
        byte[] bytes = ByteBuffer.allocate(8).putLong(value).array();
        baos.write(bytes, 0, bytes.length);
    }

    private static void writeString(ByteArrayOutputStream baos, String value) {
        byte[] bytes = value != null ? value.getBytes(StandardCharsets.UTF_8) : new byte[0];
        baos.write(bytes, 0, bytes.length);
    }

    private static void writeBytes(ByteArrayOutputStream baos, byte[] value) {
        byte[] bytes = value != null ? value : new byte[0];
        baos.write(bytes, 0, bytes.length);
    }

    private static void writeWorldState(ByteArrayOutputStream baos, WorldState worldState) {
        if (worldState == null || worldState.accounts == null) {
            writeInt(baos, 0);
            return;
        }

        List<Address> accountAddresses = new ArrayList<>(worldState.accounts.keySet());
        Collections.sort(accountAddresses);

        for (Address address : accountAddresses) {
            Account account = worldState.accounts.get(address);
            writeString(baos, address.toHexString());
            writeAccount(baos, account);
        }
    }

    private static void writeAccount(ByteArrayOutputStream baos, Account account) {
        if (account == null) {
            writeInt(baos, 0);
            return;
        }

        writeLong(baos, account.balance);
        writeLong(baos, account.nonce_count);
        writeBytes(baos, account.code);

        if (account.storage == null) {
            writeInt(baos, 0);
            return;
        }

        List<String> storageKeys = new ArrayList<>(account.storage.keySet());
        Collections.sort(storageKeys);

        for (String key : storageKeys) {
            writeString(baos, key);
            writeString(baos, account.storage.get(key));
        }
    }

}

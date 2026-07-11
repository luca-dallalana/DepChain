package client;
import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import blockchain.GetAllowance;
import blockchain.GetBalance;
import blockchain.Transaction;
import blockchain.TransactionResponse;
import blockchain.evm.ABIEncoder;
import config.ClientConfig;
import crypto.CryptoLib;
import info.ReplicaInfo;
import network.DeliveryListener;
import network.GsonUtils;
import network.NetworkLayerLib;
import network.UdpReceiver;

public class Client implements DeliveryListener{
    private ClientConfig config;
    private DatagramSocket socket;
    private boolean running = true;
    private NetworkLayerLib networkLayerLib;
    private UdpReceiver receiver;
    private final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, String>> pendingDecisions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, String>> pendingReads = new ConcurrentHashMap<>();
    private int ReadSequence = 0;      // For tracking request/response pairs (all operations)
    private int transactionNonce = 0;    // For blockchain transactions only (increments on success)

    public Client(ClientConfig config, DatagramSocket socket) {
        this.config = config;
        this.socket = socket;
        this.networkLayerLib = new NetworkLayerLib(this, socket);

    }
    
    public void start() {
        System.out.println("===== Client STARTED =====");
        this.receiver = new UdpReceiver(socket, networkLayerLib);
        new Thread(receiver).start();
        startCLI();
    }
    public void startCLI() {
        
        System.out.println("\n=========== Client ==========");
        System.out.println("Commands:");
        System.out.println("  0 - DepCoin Transfer (native)");
        System.out.println("  1 - ISTCoin Transfer (contract)");
        System.out.println("  2 - Set Allowance");
        System.out.println("  3 - TransferFrom");
        System.out.println("  4 - Get DepCoin Balance");
        System.out.println("  5 - Get ISTCoin Balance");
        System.out.println("  6 - Get Allowance");
        System.out.println("  7 - Exit");
        System.out.println("===============================\n");

        Scanner scanner = new Scanner(System.in);
        while (this.running) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;
            switch (input) {
                case "7":
                    scanner.close();
                    return;
                case "0":
                    // Native DepCoin transfer
                    Address toAddress = readAddressForClient(scanner, "Enter recipient client ID: ");
                    Long transferValue = readLong(scanner, "Enter value: ");
                    Long gasLimit = 21000L; // Fixed gas limit for native transfer
                    Long maxFeePerGas = 1L;
                    Long maxPriorityFeePerGas = 0L;
                    Address fromAddress = config.getAccountAddress(config.getID());

                    Transaction DepCoinTransferRequest = new Transaction(
                        config.getPort(),
                        fromAddress,
                        toAddress,
                        transferValue,
                        null,
                        gasLimit,
                        maxFeePerGas,
                        maxPriorityFeePerGas,
                        transactionNonce++,
                        null
                    );

                    try {
                        sendTransaction(DepCoinTransferRequest);
                    } catch (Exception e) {
                        System.err.println("Error sending DepCoin transfer: " + e.getMessage());
                    }
                    break;
                case "1":
                    // ISTCoin transfer through contract
                    Address recipientAddress = readAddressForClient(scanner, "Enter recipient client ID: ");
                    Long istValue = readLong(scanner, "Enter value: ");
                    Long istGasLimit = readLong(scanner, "Enter gasLimit: ");
                    Long istMaxFeePerGas = readLong(scanner, "Enter maxFeePerGas: ");
                    Long istMaxPriorityFeePerGas = readLong(scanner, "Enter maxPriorityFeePerGas: ");
                    Address senderAddress = config.getAccountAddress(config.getID());
                    Bytes transferData = ABIEncoder.encodeTransfer(recipientAddress, BigInteger.valueOf(istValue));

                    Transaction istCoinTransferRequest = new Transaction(
                        config.getPort(),
                        senderAddress,
                        config.getISTCoinContractAddress(),
                        0L,
                        transferData.toArray(),
                        istGasLimit,
                        istMaxFeePerGas,
                        istMaxPriorityFeePerGas,
                        transactionNonce++,
                        null
                    );

                    try {
                        sendTransaction(istCoinTransferRequest);
                    } catch (Exception e) {
                        System.err.println("Error sending ISTCoin transfer: " + e.getMessage());
                    }
                    break;
                case "2":
                    // Set allowance on ISTCoin contract via approve
                    Address spenderAddress = readAddressForClient(scanner, "Enter spender client ID: ");
                    Long newAllowance = readLongAllowZero(scanner, "Enter new allowance value: ");
                    Long expectedCurrentAllowance = readLongAllowZero(scanner, "Enter expected current allowance value: ");
                    Long allowanceGasLimit = readLong(scanner, "Enter gasLimit: ");
                    Long allowanceMaxFeePerGas = readLong(scanner, "Enter maxFeePerGas: ");
                    Long allowanceMaxPriorityFeePerGas = readLong(scanner, "Enter maxPriorityFeePerGas: ");
                    Address ownerAddress = config.getAccountAddress(config.getID());

                    Bytes allowanceData = ABIEncoder.encodeApprove(
                        spenderAddress,
                        BigInteger.valueOf(newAllowance),
                        BigInteger.valueOf(expectedCurrentAllowance)
                    );

                    Transaction allowanceRequest = new Transaction(
                        config.getPort(),
                        ownerAddress,
                        config.getISTCoinContractAddress(),
                        0L,
                        allowanceData.toArray(),
                        allowanceGasLimit,
                        allowanceMaxFeePerGas,
                        allowanceMaxPriorityFeePerGas,
                        transactionNonce++,
                        null
                    );

                    try {
                        sendTransaction(allowanceRequest);
                    } catch (Exception e) {
                        System.err.println("Error sending allowance change: " + e.getMessage());
                    }
                    break;
                case "3":
                    // TransferFrom
                    Address fromAddr = readAddressForClient(scanner, "Enter account owner client ID: ");
                    Address toTfAddress = readAddressForClient(scanner, "Enter recipient client ID: ");
                    Long transferFromValue = readLong(scanner, "Enter value: ");
                    Long gasLimitTF = readLong(scanner, "Enter gasLimit: ");
                    Long maxFeePerGasTF = readLong(scanner, "Enter maxFeePerGas: ");
                    Long maxPriorityFeePerGasTF = readLong(scanner, "Enter maxPriorityFeePerGas: ");
                    Address spenderAddr = config.getAccountAddress(config.getID());
                    Bytes transferFromData = ABIEncoder.encodeTransferFrom(fromAddr, toTfAddress, BigInteger.valueOf(transferFromValue));

                    Transaction transferFromRequest = new Transaction(
                        config.getPort(),
                        spenderAddr,
                        config.getISTCoinContractAddress(),
                        0L,
                        transferFromData.toArray(),
                        gasLimitTF,
                        maxFeePerGasTF,
                        maxPriorityFeePerGasTF,
                        transactionNonce++,
                        null
                    );

                    try {
                        sendTransaction(transferFromRequest);
                    } catch (Exception e) {
                        System.err.println("Error sending transferFrom: " + e.getMessage());
                    }
                    break;
                case "4":
                    Address depBalanceAddress = selectAddressFromList(scanner, "Choose address to check DepCoin balance:");
                    if (depBalanceAddress == null) {
                        break;
                    }
                    try {
                        sendGetBalance(depBalanceAddress, "DepCoin", ReadSequence);
                    } catch (IOException e) {
                        System.err.println("Error requesting DepCoin balance: " + e.getMessage());
                    }
                    ReadSequence++;
                    break;
                case "5":
                    Address istBalanceAddress = selectAddressFromList(scanner, "Choose address to check ISTCoin balance:");
                    if (istBalanceAddress == null) {
                        break;
                    }
                    try {
                        sendGetBalance(istBalanceAddress, "ISTCoin", ReadSequence);
                    } catch (IOException e) {
                        System.err.println("Error requesting ISTCoin balance: " + e.getMessage());
                    }
                    ReadSequence++;
                    break;
                case "6":
                    // Get Allowance (only for ISTCoin - allowances don't exist for native DepCoin)
                    Address ownerAddressForAllowance = selectAddressFromList(scanner, "Choose owner address to check allowance:");
                    if (ownerAddressForAllowance == null) {
                        break;
                    }
                    Address spenderAddressForAllowance = readAddressForClient(scanner, "Enter spender client ID: ");
                    try {
                        sendGetAllowance(ownerAddressForAllowance, spenderAddressForAllowance, ReadSequence);
                    } catch (IOException e) {
                        System.err.println("Error requesting allowance: " + e.getMessage());
                    }
                    ReadSequence++;
                    break;
                default:
                    System.out.println("Unknown command. Try 0 (DepCoin), 1 (IST transfer), 2 (Set Allowance), 3 (TransferFrom), 4 (Get DepCoin Balance), 5 (Get ISTCoin Balance), 6 (Get Allowance), or 7 (Exit)");
            }
        }
    }

    private void sendTransaction(Transaction request) throws Exception {
        pendingDecisions.put(request.nonce_count, new ConcurrentHashMap<>());
        String PRIVATE_KEY_PATH = "../rsa_keys/client_" + config.getID() + "/client_" + config.getID() + ".privatekey";
        String packet = "NewTransaction=";
        try {
            String unsignedJson = GsonUtils.GSON.toJson(request);
            byte[] signature = CryptoLib.sign(unsignedJson.getBytes(), PRIVATE_KEY_PATH);
            request.signature = signature;
            String json = GsonUtils.GSON.toJson(request);
            packet += json;
        } catch (Exception e) {
            e.printStackTrace();
        }
        sendMessage(packet);
    }

    private void sendGetBalance(Address address, String coin, int seq) throws IOException{
        pendingReads.put(seq, new ConcurrentHashMap<>());
        GetBalance getBalanceRequest = new GetBalance(address, coin, null, -1, seq);
        String PRIVATE_KEY_PATH = "../rsa_keys/client_" + config.getID() + "/client_" + config.getID() + ".privatekey";
        String packet = "GetBalance=";
        try {
            String unsignedJson = GsonUtils.GSON.toJson(getBalanceRequest);
            byte[] signature = CryptoLib.sign(unsignedJson.getBytes(), PRIVATE_KEY_PATH);
            getBalanceRequest.setSignature(signature);
            String json = GsonUtils.GSON.toJson(getBalanceRequest);
            packet += json;
        } catch (Exception e) {
            e.printStackTrace();
        }
        sendMessage(packet);
    }

    private void sendGetAllowance(Address owner, Address spender, int seq) throws IOException{
        pendingReads.put(seq, new ConcurrentHashMap<>());
        GetAllowance getAllowanceRequest = new GetAllowance(owner, spender, null, -1, seq);
        String PRIVATE_KEY_PATH = "../rsa_keys/client_" + config.getID() + "/client_" + config.getID() + ".privatekey";
        String packet = "GetAllowance=";
        try {
            String unsignedJson = GsonUtils.GSON.toJson(getAllowanceRequest);
            byte[] signature = CryptoLib.sign(unsignedJson.getBytes(), PRIVATE_KEY_PATH);
            getAllowanceRequest.setSignature(signature);
            String json = GsonUtils.GSON.toJson(getAllowanceRequest);
            packet += json;
        } catch (Exception e) {
            e.printStackTrace();
        }
        sendMessage(packet);
    }

    private void sendMessage(String packet) throws IOException {
        for (ReplicaInfo replica : config.getAllReplicas()) {
            networkLayerLib.alpSend(packet, replica.getIP(), replica.getPort());
        }
    }

    @Override
    public void onDeliver(int senderPort, String message) {
        
        String payload = message;
        
        if (payload.startsWith("SEQ=")) {
            int idx = payload.indexOf(' ');
            if (idx != -1) {
                payload = payload.substring(idx + 1);
            }
        }
        if (payload.startsWith("DECIDED=")) { // DECIDED=5;REPLY=OK 
            String[] parts = payload.split(";");
            String seqNum = parts[1].split("=")[1];
            String reply = parts[2].split("=")[1];
            System.out.println("Received reply: " + reply);
            registerDecidedReply(Integer.parseInt(seqNum), senderPort, reply);
        }
        if (payload.startsWith("GetBalanceResponse=")) {
            String json = payload.substring("GetBalanceResponse=".length());
            GetBalance getBalanceReply = GsonUtils.GSON.fromJson(json, GetBalance.class);
            String reply = Long.toString(getBalanceReply.getBalance());
            System.out.println("Balance for " + getBalanceReply.getAddress() + " (" + getBalanceReply.getCoin() + "): " + getBalanceReply.getBalance());
            registerDecidedRead(getBalanceReply.getSequenceNumber(), senderPort, reply);
        }
        if (payload.startsWith("GetAllowanceResponse=")) {
            String json = payload.substring("GetAllowanceResponse=".length());
            GetAllowance getAllowanceReply = GsonUtils.GSON.fromJson(json, GetAllowance.class);
            String reply = Long.toString(getAllowanceReply.getAllowance());
            System.out.println("Allowance for owner= " + getAllowanceReply.getOwner() +
                             " spender= " + getAllowanceReply.getSpender() +
                             " is: " + getAllowanceReply.getAllowance());
            registerDecidedRead(getAllowanceReply.getSequenceNumber(), senderPort, reply);
        }
        if (payload.startsWith("TransactionResponse=")) {
            String json = payload.substring("TransactionResponse=".length());
            TransactionResponse txResponse = GsonUtils.GSON.fromJson(json, TransactionResponse.class);
            String reply = txResponse.getExecutionSuccess() ? "SUCCESS" : "FAILED";
            System.out.println("Transaction nonce=" + txResponse.getSequenceNumber() +
                              " execution status: " + reply);
            registerDecidedReply(txResponse.getSequenceNumber(), senderPort, reply);
        }
    }

    private void registerDecidedReply(int sequence, int senderPort, String reply) {
        ConcurrentHashMap<Integer, String> repliesByReplica = pendingDecisions.get(sequence);
        if (repliesByReplica == null) {
            return;
        }

        if (repliesByReplica.putIfAbsent(senderPort, reply) == null) {
            long matchingReplies = repliesByReplica.values().stream()
                .filter(reply::equals)
                .count();
            if (matchingReplies >= config.getF() + 1){
                System.out.println("Transaction seq=" + sequence + " decided: " + reply);
                pendingDecisions.remove(sequence, repliesByReplica);
                System.out.print("\n> ");
                System.out.flush();
            }
        }
    }

    private void registerDecidedRead(int sequence, int senderPort, String reply) {
        ConcurrentHashMap<Integer, String> repliesByReplica = pendingReads.get(sequence);
        if (repliesByReplica == null) {
            return;
        }

        if (repliesByReplica.putIfAbsent(senderPort, reply) == null) {
            long matchingReplies = repliesByReplica.values().stream()
                .filter(reply::equals)
                .count();
            if (matchingReplies >= config.getF() + 1){
                System.out.println("Read seq=" + sequence + " decided: " + reply);
                pendingReads.remove(sequence, repliesByReplica);
                System.out.print("\n> ");
                System.out.flush();
            }
            
            if (repliesByReplica.size() == config.getQuorumSize()) {
                System.out.println("Read seq=" + sequence + " Failed please try again");
                pendingReads.remove(sequence, repliesByReplica);
                System.out.print("\n> ");
                System.out.flush();
            }
        }
    }

    private Address readAddressForClient(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            int id;
            try {
                id = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid client ID. Please enter a number.");
                continue;
            }

            if (id < 0 || id >= config.getN()) {
                System.out.println("Invalid client ID. Valid range is 0 to " + (config.getN() - 1) + ".");
                continue;
            }

            List<Address> clientAddresses = config.getAccountAddresses(id);
            if (clientAddresses.isEmpty()) {
                System.out.println("No address configured for client " + id + ".");
                continue;
            }

            System.out.println("Available addresses for client " + id + ":");
            for (int i = 0; i < clientAddresses.size(); i++) {
                System.out.println("  " + (i + 1) + " - " + clientAddresses.get(i));
            }

            while (true) {
                System.out.print("Select option number: ");
                String optionInput = scanner.nextLine().trim();
                int option;
                try {
                    option = Integer.parseInt(optionInput);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid option. Please enter a number.");
                    continue;
                }

                if (option < 1 || option > clientAddresses.size()) {
                    System.out.println("Invalid option. Valid range is 1 to " + clientAddresses.size() + ".");
                    continue;
                }

                return clientAddresses.get(option - 1);
            }
        }
    }

    private Address selectAddressFromList(Scanner scanner, String prompt) {
        List<Address> ownAddresses = new ArrayList<>(config.getAccountAddresses(config.getID()));

        if (ownAddresses.isEmpty()) {
            System.out.println("No accounts available for client " + config.getID() + ".");
            return null;
        }

        System.out.println(prompt);
        for (int i = 0; i < ownAddresses.size(); i++) {
            System.out.println("  " + (i + 1) + " - " + ownAddresses.get(i));
        }

        while (true) {
            System.out.print("Select option number: ");
            String input = scanner.nextLine().trim();
            int option;
            try {
                option = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid option. Please enter a number.");
                continue;
            }

            if (option < 1 || option > ownAddresses.size()) {
                System.out.println("Invalid option. Valid range is 1 to " + ownAddresses.size() + ".");
                continue;
            }

            return ownAddresses.get(option - 1);
        }
    }

    private Long readLong(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            long value;
            try {
                value = Long.parseLong(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid number: " + input);
                continue;
            }

            if (value <= 0) {
                System.out.println("Value must be bigger then zero.");
                continue;
            }

            return value;
        }
    }

    private Long readLongAllowZero(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            long value;
            try {
                value = Long.parseLong(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid number: " + input);
                continue;
            }

            if (value < 0) {
                System.out.println("Value must be zero or positive.");
                continue;
            }

            return value;
        }
    }

}

package client;
import java.math.BigInteger;
import java.net.DatagramSocket;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import blockchain.Transaction;
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
    private int sequenceNumber = 0;

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
        System.out.println("  2 - Approve Allowance");
        System.out.println("  3 - TransferFrom");
        System.out.println("  4 - Exit");
        System.out.println("===============================\n");

        Scanner scanner = new Scanner(System.in);
        while (this.running) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;
            switch (input) {
                case "4":
                    scanner.close();
                    return;
                case "0":
                    // Native DepCoin transfer
                    Address toAddress = readClientId(scanner, "Enter recipient: ");
                    Long transferValue = readLong(scanner, "Enter value: ");
                    Long gasLimit = readLong(scanner, "Enter gasLimit: ");
                    Long gasPrice = readLong(scanner, "Enter gasPrice: ");
                    Address fromAddress = config.getAccountAddress(config.getID());

                    sequenceNumber++;

                    Transaction DepCoinTransferRequest = new Transaction(
                        config.getPort(),
                        fromAddress,
                        toAddress,
                        transferValue,
                        null,
                        gasLimit,
                        gasPrice,
                        sequenceNumber,
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
                    Address recipientAddress = readClientId(scanner, "Enter recipient: ");
                    Long istValue = readLong(scanner, "Enter value: ");
                    Long istGasLimit = readLong(scanner, "Enter gasLimit: ");
                    Long istGasPrice = readLong(scanner, "Enter gasPrice: ");
                    Address senderAddress = config.getAccountAddress(config.getID());
                    Bytes transferData = ABIEncoder.encodeTransfer(recipientAddress, BigInteger.valueOf(istValue));

                    sequenceNumber++;

                    Transaction istCoinTransferRequest = new Transaction(
                        config.getPort(),
                        senderAddress,
                        config.getISTCoinContractAddress(),
                        0L,
                        transferData.toArray(),
                        istGasLimit,
                        istGasPrice,
                        sequenceNumber,
                        null
                    );

                    try {
                        sendTransaction(istCoinTransferRequest);
                    } catch (Exception e) {
                        System.err.println("Error sending ISTCoin transfer: " + e.getMessage());
                    }
                    break;
                case "2":
                    // Approve allowance on ISTCoin contract
                    Address spenderAddress = readClientId(scanner, "Enter spender: ");
                    Long newAllowance = readLong(scanner, "Enter new allowance value: ");
                    Long expectedAllowance = readLong(scanner, "Enter expected current allowance: ");
                    Long approveGasLimit = readLong(scanner, "Enter gasLimit: ");
                    Long approveGasPrice = readLong(scanner, "Enter gasPrice: ");
                    Address ownerAddress = config.getAccountAddress(config.getID());
                    Bytes approveData = ABIEncoder.encodeApprove(
                        spenderAddress,
                        BigInteger.valueOf(newAllowance),
                        BigInteger.valueOf(expectedAllowance)
                    );

                    sequenceNumber++;

                    Transaction approveRequest = new Transaction(
                        config.getPort(),
                        ownerAddress,
                        config.getISTCoinContractAddress(),
                        0L,
                        approveData.toArray(),
                        approveGasLimit,
                        approveGasPrice,
                        sequenceNumber,
                        null
                    );

                    try {
                        sendTransaction(approveRequest);
                    } catch (Exception e) {
                        System.err.println("Error sending approve: " + e.getMessage());
                    }
                    break;
                case "3":
                    // TransferFrom
                    Address fromAddr = readClientId(scanner, "Enter account owner: ");
                    Address toTfAddress = readClientId(scanner, "Enter recipient: ");
                    Long transferFromValue = readLong(scanner, "Enter value: ");
                    Long gasLimitTF = readLong(scanner, "Enter gasLimit: ");
                    Long gasPriceTF = readLong(scanner, "Enter gasPrice: ");
                    Address spenderAddr = config.getAccountAddress(config.getID());
                    Bytes transferFromData = ABIEncoder.encodeTransferFrom(fromAddr, toTfAddress, BigInteger.valueOf(transferFromValue));

                    sequenceNumber++;
                    
                    Transaction transferFromRequest = new Transaction(
                        config.getPort(),
                        spenderAddr,
                        config.getISTCoinContractAddress(),
                        0L,
                        transferFromData.toArray(),
                        gasLimitTF,
                        gasPriceTF,
                        sequenceNumber,
                        null
                    );
                    try {
                        sendTransaction(transferFromRequest);
                    } catch (Exception e) {
                        System.err.println("Error sending transferFrom: " + e.getMessage());
                    }
                    break;
                default:
                    System.out.println("Unknown command. Try 0 (DepCoin), 1 (IST transfer), 2 (Approve), 3 (TransferFrom), or 4 (Exit)");
            }
        }
    }

    private void sendTransaction(Transaction request) throws Exception {
        pendingDecisions.put((int) request.nonce_count, new ConcurrentHashMap<>());
        sendMessage(request);
    }

    private void sendMessage(Transaction request) throws java.io.IOException {
        String packet = "NewCommand=";
        String PRIVATE_KEY_PATH = "../rsa_keys/client_" + config.getID() + "/client_" + config.getID() + ".privatekey";
        try {
            String unsignedJson = GsonUtils.GSON.toJson(request);
            byte[] signature = CryptoLib.sign(unsignedJson.getBytes(), PRIVATE_KEY_PATH);
            request.signature = signature;
            String json = GsonUtils.GSON.toJson(request);
            System.out.println("Sending JSON: " + json);
            packet += json;
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        if (payload.startsWith("DECIDED=")) { // DECIDED=5;REPLY=OK //FIXME maybe send a json
            String[] parts = payload.split(";");
            String seqNum = parts[1].split("=")[1];
            String reply = parts[2].split("=")[1];
            System.out.println("Received reply: " + reply);
            registerDecidedReply(Integer.parseInt(seqNum), senderPort, reply);
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
            }
        }
    }

    private Address readClientId(Scanner scanner, String prompt) {
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

            if (config.getAccountAddress(id) == null) {
                System.out.println("No address configured for client " + id + ".");
                continue;
            }

            return config.getAccountAddress(id);
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

}

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
    private ConcurrentHashMap<Integer, String> receivedDecided = new ConcurrentHashMap<>(); //maps port -> decided command
    private boolean decided = false;
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
        System.out.println("  0 - Transfer");
        System.out.println("  1 - TransferFrom");
        System.out.println("  2 - Exit");
        System.out.println("===============================\n");

        Scanner scanner = new Scanner(System.in);
        while (this.running) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;
            switch (input) {
                case "2":
                    scanner.close();
                    return;
                case "0":
                    // Transfer
                    System.out.print("Enter recipient: ");
                    String to = scanner.nextLine().trim();
                    System.out.print("Enter value: ");
                    String value = scanner.nextLine().trim();
                    System.out.print("Enter gasLimit: ");
                    String gasLimit = scanner.nextLine().trim();
                    System.out.print("Enter gasPrice: ");
                    String gasPrice = scanner.nextLine().trim();
                    sequenceNumber++;
                    int toId = Integer.parseInt(to);
                    long transferValue = Long.parseLong(value);
                    Address fromAddress = config.getAccountAddress(config.getID());
                    Address toAddress = config.getAccountAddress(toId);
                    Bytes transferData = ABIEncoder.encodeTransfer(toAddress, BigInteger.valueOf(transferValue));

                    Transaction transferRequest = new Transaction(fromAddress, toAddress,
                    0L,transferData.toArray(),Long.parseLong(gasLimit),Long.parseLong(gasPrice),sequenceNumber,null);

                    try {
                        sendMessage(transferRequest);
                        while (!decided) {
                            Thread.sleep(100);
                        }
                        decided = false;
                        receivedDecided.clear();
                    } catch (Exception e) {
                        System.err.println("Error sending transfer: " + e.getMessage());
                    }
                    break;
                case "1": 
                    // TransferFrom
                    System.out.print("Enter account owner: ");
                    String fromTF = scanner.nextLine().trim();
                    System.out.print("Enter recipient: ");
                    String toTF = scanner.nextLine().trim();
                    System.out.print("Enter value: ");
                    String valueTF = scanner.nextLine().trim();
                    System.out.print("Enter gasLimit: ");
                    String gasLimitTF = scanner.nextLine().trim();
                    System.out.print("Enter gasPrice: ");
                    String gasPriceTF = scanner.nextLine().trim();
                    sequenceNumber++;
                    int fromId = Integer.parseInt(fromTF);
                    int toTfId = Integer.parseInt(toTF);
                    long transferFromValue = Long.parseLong(valueTF);
                    Address fromAddr = config.getAccountAddress(fromId);
                    Address toTfAddress = config.getAccountAddress(toTfId);
                    Bytes transferFromData = ABIEncoder.encodeTransferFrom(fromAddr, toTfAddress, BigInteger.valueOf(transferFromValue));
                    
                    Transaction transferFromRequest = new Transaction(fromAddr,toTfAddress,0L,transferFromData.toArray(),
                        Long.parseLong(gasLimitTF),Long.parseLong(gasPriceTF),sequenceNumber,null);
                    try {
                        sendMessage(transferFromRequest);
                        while (!decided) {
                            Thread.sleep(100);
                        }
                        decided = false;
                        receivedDecided.clear();
                    } catch (Exception e) {
                        System.err.println("Error sending transferFrom: " + e.getMessage());
                    }
                    break;
                default:
                    System.out.println("Unknown command. Try 0 (Transfer), 1 (TransferFrom), or 2 (Exit)");
            }
        }
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
        if (payload.startsWith("DECIDED=")) {
            String reply = payload.substring("DECIDED=".length());
            System.out.println("Received reply: " + reply);
            addDecidedCommand(senderPort, reply);
        }
    }

    private void addDecidedCommand(int port, String command) {
        if (receivedDecided.containsKey(port)) return;
        receivedDecided.put(port, command);
        long count = receivedDecided.values().stream()
            .filter(c -> c.equals(command)).count();
        if (count >= config.getF() + 1){
            decided = true;
            System.out.println("The command: " + command + " was decided.");
        }
        
    }

}

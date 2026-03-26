package member;

import java.io.IOException;
import java.net.DatagramSocket;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;

import blockchain.Block;
import blockchain.BlockStore;
import blockchain.BlockchainMember;
import blockchain.GetBalance;
import blockchain.Transaction;
import blockchain.TransactionResponse;
import config.MemberConfig;
import consensus.QCManager;
import crypto.CryptoLib;
import info.ReplicaInfo;
import model.CatchUp;
import model.Message;
import model.QC;
import network.DeliveryListener;
import network.GsonUtils;
import network.NetworkLayerLib;
import network.UdpReceiver;
import util.DepChainUtil;
import org.hyperledger.besu.datatypes.Address;
import blockchain.Account;

public class DepChainMember implements DeliveryListener{
    private NetworkLayerLib networkLayerLib;
    private int curView;          // current view number
    private QC lockedQC;         // the highest QC for which this replica voted "commit"
    private QC prepareQC;        // the highest QC for which this replica voted "pre-commit"

    private DatagramSocket socket;
    private UdpReceiver receiver;
    private BlockStore blockStore;

    private final MemberConfig memberConfig; // All replica information
    private final QCManager qcManager;
    private final DepChainUtil util;
    private final Map<String, Block> pendingPreparedBlocks; // Stores blocks that the replica voted in prepare but not yet presisted in pre-commit
    private Block currentProposal; // Track leader's proposal
    private int lastExecutedHeight; // Track last executed command height
    private Block lastExecutedBlock; // Track last executed block
    private int timeoutCount = 1; // Track number of timeouts for exponential backoff

    private static final long PHASE_TIMEOUT_MS = 8000;  // FIXME check this
    private Thread timeoutThread;

    public DepChainMember(MemberConfig memberConfig, DatagramSocket socket) {
        this.memberConfig = memberConfig;
        this.socket = socket;

        // Initialize consensus state
        this.curView = 0;
        this.lockedQC = null;
        this.prepareQC = null;
        this.networkLayerLib = new NetworkLayerLib(this, socket);
        this.lastExecutedHeight = 0;

        // Initialize QC management
        this.qcManager = new QCManager(memberConfig);
        this.util = new DepChainUtil(qcManager);
        this.pendingPreparedBlocks = new ConcurrentHashMap<>();

    }


    public boolean safeBlock(Block block, QC qc) {
        if (lockedQC == null) {
            return blockStore.extendsFrom(block, qc.blockHash);
        }
        //System.out.println("======= lockedQC node: " + lockedQC.blockHash + " view: " + lockedQC.viewNumber);
        //System.out.println("======= justify node: " + qc.blockHash + " view: " + qc.viewNumber);
        // Safety (Extends from lockedQC.blockHash) Liveness (QC has higher view than locked QC)
        return blockStore.extendsFrom(block, lockedQC.blockHash) || qc.viewNumber > lockedQC.viewNumber;
    }

    public void setNetworkLayerLib(NetworkLayerLib networkLayerLib) {
        this.networkLayerLib = networkLayerLib;
    }

    @Override
    public void onDeliver(int senderPort, String message) {

        //System.out.println("--------------------------------");
        //System.out.println("Member received message from sender " + senderPort + ": " + message);
        //System.out.println("--------------------------------\n");
        String payload = message;
        if (payload.startsWith("SEQ=")) {
            int idx = payload.indexOf(' ');
            if (idx != -1) {
                payload = payload.substring(idx + 1);
            }
        }
        if(payload.startsWith("GetBalance=")){
            String json = payload.substring("GetBalance=".length()).trim();
            GetBalance request = GsonUtils.GSON.fromJson(json, GetBalance.class);
            
            if (senderPort < 4000){ 
                System.err.println("GetBalance request received is not from a client, ignoring");
                return;
            }
            
            if (request.getSignature() == null) { 
                System.err.println("Invalid signature for GetBalance request in message, ignoring");
                return;
            }

            int senderId = senderPort - 4000;
            String PUBLIC_KEY_PATH = "../rsa_keys/client_" + senderId + "/client_" + senderId + ".pubkey";

            GetBalance unsignedRequest = new GetBalance(request.getAddress(), request.getCoin(), null, -1,request.getSequenceNumber()); // create a GetBalance object without the signature for verification
            byte[] requestBytes = GsonUtils.GSON.toJson(unsignedRequest).getBytes();

            try {
                if(!CryptoLib.verifySignature(requestBytes, request.getSignature(), PUBLIC_KEY_PATH)){
                    System.err.println("Invalid signature for GetBalance request in message, ignoring"); 
                    return;
                }
            } catch (Exception e) {
                System.err.println("Error verifying signature for GetBalance request: " + e.getMessage());
                return;
            }
            String coin = request.getCoin();
            if (coin.equals("DepCoin")) {
                long balance = lastExecutedBlock.state.getAccount(request.getAddress()).getBalance();
                sendBalanceResponse(request, balance, senderPort);
                return;
            }

            if (coin.equals("ISTCoin")) {
                Address istCoinAddress = Address.fromHexString(Block.IST_COIN_ADDRESS);
                Account istCoinAccount = lastExecutedBlock.state.getAccount(istCoinAddress);

                String storageKey = GetBalance.computeMappingStorageKey(request.getAddress().toHexString());

                String balanceHex = istCoinAccount.getStorage().get(storageKey);

                long balance = 0;
                if (balanceHex != null && !balanceHex.equals("0x0")) {
                    balance = Long.parseLong(balanceHex.substring(2), 16);
                }

                sendBalanceResponse(request, balance, senderPort);
                return;
            }
            return;
        }
        if (payload.startsWith("NewTransaction=")) {
            String json = payload.substring("NewTransaction=".length()).trim();
            Transaction request = GsonUtils.GSON.fromJson(json, Transaction.class);
            
            if (senderPort < 4000){ //FIXME we should send an error message back to the client instead of just ignoring
                System.err.println("Command received is not from a client, ignoring");
                return;
            }
            
            if (request.signature == null) { //FIXME we should send an error message back to the client instead of just ignoring
                System.err.println("Invalid signature for client command in message, ignoring");
                return;
            }

            if (request.gasLimit <= 0 || request.gasPrice <= 0) {
                System.err.println("Invalid gas limit or gas price in client command, ignoring"); //FIXME we should send an error message back to the client instead of just ignoring
                return;
            }

            if (request.senderPort != senderPort) {
                System.err.println("Sender port in transaction does not match actual sender port, ignoring");//FIXME we should send an error message back to the client instead of just ignoring
                return;
            }

            int senderId = senderPort - 4000;
            String PUBLIC_KEY_PATH = "../rsa_keys/client_" + senderId + "/client_" + senderId + ".pubkey";

            Transaction unsignedTx = new Transaction(request.senderPort, request.from, request.to, request.value, request.data, request.gasLimit, request.gasPrice, request.nonce_count, null); // create a transaction object without the signature for verification
            byte[] transactionBytes = GsonUtils.GSON.toJson(unsignedTx).getBytes();

            try {
                if(!CryptoLib.verifySignature(transactionBytes, request.signature, PUBLIC_KEY_PATH)){
                    System.err.println("Invalid signature for client command in message, ignoring"); //FIXME we should send an error message back to the client instead of just ignoring
                    return;
                }
            } catch (Exception e) {
                System.err.println("Error verifying signature for client command: " + e.getMessage());
                return;
            }

            System.out.println("Received new command: " + request.from + " -> " + request.to + " value: " + request.value + " gasLimit: " + request.gasLimit + " gasPrice: " + request.gasPrice + " seq: " + request.nonce_count);


            memberConfig.addPendingTransaction(request);
            return; 
        }

        if (payload.startsWith("CATCH-UP=")) {
            String json = payload.substring("CATCH-UP=".length()).trim();
            CatchUp m = GsonUtils.GSON.fromJson(json, CatchUp.class);
            handleCatchUpRequest(m);
            return;
        }

        if (payload.startsWith("CATCH-UP_RESPONSE=")) {
            String json = payload.substring("CATCH-UP_RESPONSE=".length()).trim();
            CatchUp m = GsonUtils.GSON.fromJson(json, CatchUp.class);
            handleCatchUpResponse(m);
            return;
        }

        Gson gson = GsonUtils.GSON;
        Message m = gson.fromJson(payload, Message.class);
        m.senderPort = senderPort; // Ensure senderPort is set

        switch (m.type) {
            case "new-view":
                if(m.viewNumber >= curView){ // the case where where i m the next leader and vote for me
                    if (qcManager.addVote(m)) {
                        handlePrepare();
                        startTimeout(); // prepare phase
                    }
                }
                break;
            case "prepare":
                if (memberConfig.isLeader(curView)) {
                    if (qcManager.addVote(m)) {
                        handlePreCommitLeader();
                        startTimeout(); // pre-commit phase
                    }
                } else {
                    handlePrepareReplica(m);
                }
                break;
            case "pre-commit":
                if (memberConfig.isLeader(curView)) {
                    if (qcManager.addVote(m)) {
                        handleCommitLeader();
                        startTimeout(); // commit phase
                    }
                } else {
                    handlePreCommitReplica(m);
                }
                break;
            case "commit":
                if (memberConfig.isLeader(curView)) {
                    if (qcManager.addVote(m)) {
                        handleDecideLeader();
                        proposeNewView();
                    }
                } else {
                    handleCommitReplica(m);
                }
                break;
            case "decide":
                handleDecideReplica(m);
                proposeNewView();
                break;
            default:
                throw new AssertionError("Unknown message type: " + m.type);
        }
    }
    
    public void start() {
        System.out.println("SERVER STARTED: ID=" + memberConfig.getID());

        try {
            Block genesis = Block.createAndSaveGenesis("..");
            qcManager.setGenesisBlockHash(genesis.blockHash);
            this.blockStore = new BlockStore(genesis);
            this.lastExecutedBlock = genesis;
            prepareQC = new QC("prepare", 0, genesis.blockHash, null);
            System.out.println("Genesis block initialized: " + genesis.blockHash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create genesis block", e);
        }

        this.receiver = new UdpReceiver(socket, networkLayerLib);
        new Thread(receiver).start();
        startTimeout();
    }

    private void sendMessage(Message m, String destIp, int destPort) throws java.io.IOException {
        String json = GsonUtils.GSON.toJson(m);
        networkLayerLib.alpSend(json, destIp, destPort);
    }

    private void broadcast(Message m) throws java.io.IOException {
        for (ReplicaInfo replica : memberConfig.getAllReplicas()) {
            sendMessage(m, replica.getIP(), replica.getPort());
        }
    }

    private void handlePrepare(){
        QC maxQC = getMaxQC();  // Read new-view votes first
        
        if (maxQC.viewNumber > prepareQC.viewNumber) {
            System.out.println("Received a maxQC bigger then my prepareQC" );
            sendCatchUpRequest(maxQC.blockHash);
        }

        // Clear new-view votes to prevent multiple calls to handlePrepare
        qcManager.clearVotesForTypeView("new-view", curView);
        try {
            Block maxQCBlock = blockStore.getBlockByHash(maxQC.blockHash);//FIXME this could go wrong if leader doesn't have the block

            if (maxQCBlock == null) { //FIXME this is for testing but its a real issue to fix
                System.err.println("MaxQC block not found in block store, -- ERROR --");
                return;
            }

            new Thread(() -> { //this is done so the thread that is listening can keep receiving messages while we do the block building that can be blocking for a period of time
                try {
                    Block curProposal = BlockchainMember.buildBlockForProposal(maxQCBlock, memberConfig.getPendingTransactions());
            
                    this.currentProposal = curProposal; // Store for QC formation

                    Message prepareMsg = util.voteMsg("prepare", curProposal.transactions, curProposal.blockHash, maxQC, curView);
                    prepareMsg.senderPort = memberConfig.getID() + 3000;
                    qcManager.addVote(prepareMsg);
                    broadcast(prepareMsg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
           return;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    
    private void handlePrepareReplica(Message m) {
        try {
            // Verify the justify QC if present

            if (m.justify == null) {
                System.err.println("Null new-view maxQC in prepare message");
                proposeNewView();
                return;
            }
            for (Transaction tx : m.transactions) {
                int senderId = tx.senderPort - 4000;
                String PUBLIC_KEY_PATH = "../rsa_keys/client_" + senderId + "/client_" + senderId + ".pubkey";
    
                Transaction unsignedTx = new Transaction(tx.senderPort, tx.from, tx.to, tx.value, tx.data, tx.gasLimit, tx.gasPrice, tx.nonce_count, null); // create a transaction object without the signature for verification
                byte[] transactionBytes = GsonUtils.GSON.toJson(unsignedTx).getBytes();
    
                try {
                    if(!CryptoLib.verifySignature(transactionBytes, tx.signature, PUBLIC_KEY_PATH)){ //FIXME ADD THE NO-OP verification
                        System.err.println("Invalid signature for client transaction in block, proposing new view");
                        proposeNewView();
                        return;
                    }
                    if(memberConfig.isDuplicateRequest(tx)) { //FIXME check the no-op case
                        System.err.println("Duplicate client command in prepare message, ignoring");
                        proposeNewView();
                        return;
                    }
                } catch (Exception e) {
                    System.err.println("Error verifying signature for client command: " + e.getMessage());
                    return;
                }
            }
      

            int matchResult = util.matchingMsg(m, curView); // 0 lower view, 1 exact match, 2 same type higher view

            if (matchResult == 0 || !cameFromLeader(m, m.viewNumber)) {
                System.err.println("Invalid late message or sender in prepare message");
                return;
            }

            if (curView != 0 && !qcManager.verifyQC(m.justify)) {
                System.err.println("Invalid justify QC in prepare message");
                proposeNewView();
                return;
            }
            
            if (matchResult == 2) {
                System.out.println("Received higher view prepare message, updating view to " + m.viewNumber);
                sendCatchUpRequest(m.justify.blockHash);
                curView = m.viewNumber; // Update to higher view
            }

            Block parentBlock = blockStore.getBlockByHash(m.justify.blockHash);
            Block proposedBlock = BlockchainMember.buildBlock(parentBlock, m.transactions); // we build the block based on the qc that supports it and the transactions sent by leader

            if (!proposedBlock.blockHash.equals(m.blockHash)) { //FIXME this makes sence right?
                System.err.println("Proposed block hash does not match block hash in prepare message");
                proposeNewView();
                return;
            }

            if (!BlockchainMember.isValidBlock(proposedBlock, parentBlock)) {
                System.err.println("Block failed state validation in prepare message");
                proposeNewView();
                return;
            }

            if (blockStore.extendsFrom(proposedBlock, m.justify.blockHash) && safeBlock(proposedBlock, m.justify)) {
                // Keep the validated proposal so we can reference it in pre-commit without rebuilding
                pendingPreparedBlocks.put(proposedBlock.blockHash, proposedBlock);
                startTimeout();
                Message voteMsg = util.voteMsg("prepare", null, m.blockHash, null, curView);
                voteMsg.senderPort = memberConfig.getID() + 3000;

                int leader = memberConfig.getLeader(curView);
                ReplicaInfo replica = memberConfig.getReplicaInfo(leader);
                sendMessage(voteMsg, replica.getIP(), replica.getPort());
            } else {
                System.err.println("block failed safety check in prepare message");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void handlePreCommitLeader() {
        try {
            prepareQC = qcManager.formQC("prepare", curView, currentProposal.blockHash);
            blockStore.storeBlock(currentProposal);
            Message preCommitMsg = util.voteMsg("pre-commit", null, currentProposal.blockHash, prepareQC, curView);
            preCommitMsg.senderPort = memberConfig.getID() + 3000;
            qcManager.addVote(preCommitMsg);
            broadcast(preCommitMsg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handlePreCommitReplica(Message m) {
        try {
            // Verify the prepareQC
            if (m.justify == null) {
                System.err.println("Null prepareQC in pre-commit message");
                proposeNewView();
                return;
            }

            int matchResult = util.matchingQC(m.justify, "prepare", curView); // 0 no match, 1 exact match, 2 same type higher view

            if (matchResult == 0 || !cameFromLeader(m, m.viewNumber)) {
                System.err.println("Invalid late prepareQC or sender in pre-commit message");
                return;
            }

            if (!qcManager.verifyQC(m.justify)){
                System.err.println("Invalid prepareQC in pre-commit message");
                System.out.println("justify view: " + m.justify.viewNumber + " current view: " + curView);
                proposeNewView();
                return;
            }

            if (matchResult == 2) {
                System.out.println("Received higher prepare message, updating view to " + m.viewNumber);
                sendCatchUpRequest(m.justify.blockHash);
                curView = m.viewNumber; // Update to higher view
            }

            startTimeout();

            Block proposedBlock = pendingPreparedBlocks.remove(m.justify.blockHash);

            if (proposedBlock == null) { // the case where we already persisted this block from an earlier path
                proposedBlock = blockStore.getBlockByHash(m.justify.blockHash);
            }

            if (proposedBlock == null || !proposedBlock.blockHash.equals(m.justify.blockHash)) {
                System.err.println("Prepared block not available or hash mismatch in pre-commit message");
                proposeNewView();
                return;
            }

            blockStore.storeBlock(proposedBlock);
            prepareQC = m.justify;

            Message voteMsg = util.voteMsg("pre-commit", null, m.justify.blockHash, m.justify, curView);
            voteMsg.senderPort = memberConfig.getID() + 3000;

            int leader = memberConfig.getLeader(curView);
            ReplicaInfo replica = memberConfig.getReplicaInfo(leader);
            sendMessage(voteMsg, replica.getIP(), replica.getPort());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleCommitLeader() {
        try {
            lockedQC = qcManager.formQC("pre-commit", curView, currentProposal.blockHash);

            Message commitMsg = util.voteMsg("commit", null, currentProposal.blockHash, lockedQC, curView);
            commitMsg.senderPort = memberConfig.getID() + 3000;
            qcManager.addVote(commitMsg);
            broadcast(commitMsg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
  


    private void handleCommitReplica(Message m) {
        try {
            // Verify the precommitQC

            if (m.justify == null) {
                System.err.println("Null precommitQC in commit message");
                proposeNewView();
                return;
            }

            int matchResult = util.matchingQC(m.justify, "pre-commit", curView); // 0 no match, 1 exact match, 2 same type higher view

            if (matchResult == 0 || !cameFromLeader(m, m.viewNumber)) {
                System.err.println("Invalid late precommitQC or sender in commit message");
                return;
            }

            if (!qcManager.verifyQC(m.justify)) {
                System.err.println("Invalid precommitQC in commit message");
                proposeNewView();
                return;
            }

            if (matchResult == 2) {
                System.out.println("Received higher view pre-commit message, updating view to " + m.viewNumber);
                sendCatchUpRequest(m.justify.blockHash);
                curView = m.viewNumber; // Update to higher view
            }
            startTimeout();

            lockedQC = m.justify;

            Message voteMsg = util.voteMsg("commit", null, m.justify.blockHash, m.justify, curView);
            voteMsg.senderPort = memberConfig.getID() + 3000;

            int leader = memberConfig.getLeader(curView);
            ReplicaInfo replica = memberConfig.getReplicaInfo(leader);
            sendMessage(voteMsg, replica.getIP(), replica.getPort());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleDecideLeader() {
        try {
            QC commitQC = qcManager.formQC("commit", curView, currentProposal.blockHash);

            Message decideMsg = DepChainUtil.Msg("decide", null, currentProposal.parentBlockHash, commitQC, curView);
            decideMsg.senderPort = memberConfig.getID() + 3000;

            broadcast(decideMsg);
            Block block = blockStore.getBlockByHash(commitQC.blockHash);
            this.lastExecutedBlock = BlockchainMember.executeBlock(block, blockStore, lastExecutedBlock);

            for (Transaction tx : block.transactions) {
                sendTransactionResponse(tx);
                if (tx.senderPort >= 4000) {
                    memberConfig.setLastSequenceForClient(tx.senderPort, tx.nonce_count);
                    memberConfig.removePendingTransaction(tx);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleDecideReplica(Message m) {
        if (m.justify == null) {
            System.err.println("Null commitQC in commit message");
            proposeNewView();
            return;
        }

        int matchResult = util.matchingQC(m.justify, "commit", curView); // 0 no match, 1 exact match, 2 same type higher view

        if (matchResult == 0 || !cameFromLeader(m, m.viewNumber)) {
            System.err.println("Invalid late commitQC or sender in commit message");
            return;
        }

        if (!qcManager.verifyQC(m.justify)) {
            System.err.println("Invalid commitQC in commit message");
            proposeNewView();
            return;
        }

        if (matchResult == 2) {
            System.out.println("Received higher view commit message, updating view to " + m.viewNumber);
            sendCatchUpRequest(m.justify.blockHash);
            curView = m.viewNumber; // Update to higher view
        }

        startTimeout();
        Block block = blockStore.getBlockByHash(m.justify.blockHash);
        this.lastExecutedBlock = BlockchainMember.executeBlock(block, blockStore, lastExecutedBlock);

        for (Transaction tx : block.transactions) {
            sendTransactionResponse(tx);
            if (tx.senderPort >= 4000) {
                memberConfig.setLastSequenceForClient(tx.senderPort, tx.nonce_count);
                memberConfig.removePendingTransaction(tx);
            }
        }
    }

    private QC getMaxQC() { //maybe put this in utils
        List<Message> msgs = qcManager.getVotes("new-view", curView);
        QC maxqc = null;
        for (Message m : msgs) {
            if (m != null && m.justify != null) {
                if (maxqc == null || m.justify.viewNumber > maxqc.viewNumber) {
                    maxqc = m.justify;
                }
            }
        }
        return maxqc;
    }

    private boolean cameFromLeader(Message m, int viewNumber) {
        return (m.senderPort - 3000) == memberConfig.getLeader(viewNumber);
    }

    // Timeout logic
    
    private void startTimeout() {
        stopTimeout();
        timeoutThread = new Thread(() -> {
            try {
                System.err.println("Starting timeout for view " + curView + " with timeout count " + timeoutCount);
                Thread.sleep(PHASE_TIMEOUT_MS * timeoutCount);
                System.out.println("timeout reached. Proposing new view.");
                if (timeoutCount < 64) {
                    timeoutCount *= 2;
                }
                proposeNewView();
            } catch (InterruptedException ignored) { timeoutCount = 1; } //reset timeout
        });
        timeoutThread.start();
    }

    private void stopTimeout() {
        if (timeoutThread != null) {
            timeoutThread.interrupt();
            timeoutThread = null;
        }
    }

    private void proposeNewView() {
        stopTimeout();
        curView++;
        System.out.println("Proposing new view: " + curView);
        Message newViewMsg = DepChainUtil.Msg("new-view", null, null ,prepareQC, curView); //FIXME maybe include here the block since the QC no longer has the full block
        newViewMsg.senderPort = memberConfig.getID() + 3000;
        int leaderID = memberConfig.getLeader(curView);
        ReplicaInfo replica = memberConfig.getReplicaInfo(leaderID);
        if (replica == null) {
            qcManager.addVote(newViewMsg);
            return;
        }
        try {
            sendMessage(newViewMsg, replica.getIP(), replica.getPort());
        } catch (IOException e) {
            e.printStackTrace();
        }
        startTimeout(); // restart for new-view phase
    }
    /*
    private void executeNode(Node node) {
        List<Node> cmdToExecute = nodeTree.getNodesUntil(node, lastExecutedHeight);
        
        lastExecutedHeight = node.height;

        for (int i = cmdToExecute.size() - 1; i >= 0; i--) { // Execute from lowest height to highest
            Node n = cmdToExecute.get(i);
            if (n.cmd.getCommand().equals("NO-OP")) {
                System.out.println("Executed NO-OP at height " + n.height);
                try {
                    Thread.sleep(3000); // FIXME this is not perfect
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                if (memberConfig.isDuplicateRequest(n.cmd)) {
                    System.out.println("Skipping duplicate command at height " + n.height + ": " + n.cmd.getCommand());
                    continue;
                }
                memberConfig.setLastSequenceForClient(n.cmd.getPort(), n.cmd.getSeq());
                System.out.println("Executed command at height " + n.height + ": " + n.cmd.getCommand() + " sending back to client " + n.cmd.getPort());
                //memberConfig.addToAppState(n.cmd.getCommand());
                memberConfig.removePendingCommand(n.cmd);
                //System.out.println("Current app state: " + memberConfig.getAppState());
                String message = "DECIDED= " + n.cmd.getCommand();
                try {
                    networkLayerLib.alpSend(message, "localhost", n.cmd.getPort());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    */
    private void sendCatchUpRequest(String receivedBlockHash) {
        String message = "CATCH-UP= "; // we send the whole but only need the height of the lockedQC block to verify
        CatchUp m = new CatchUp();
        m.viewNumber = curView;
        m.lockedQC = lockedQC;
        m.receivedBlockHash = receivedBlockHash; // the block hash that the sender of the catch-up message is currently at (the one he sent me in justify)
        m.senderPort = memberConfig.getID() + 3000;
        String json = GsonUtils.GSON.toJson(m);
        message += json;
        
        try {
            networkLayerLib.alpSend(message, "localhost", m.senderPort);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void handleCatchUpRequest(CatchUp msg){
        System.out.println("Received catch-up message with lockedQC: " + msg.lockedQC + " from sender " + msg.senderPort);
        
        if (msg.lockedQC == null || !qcManager.verifyQC(msg.lockedQC)) {
            System.err.println("Invalid QC in catch-up message");
            return;
        }
        List<Block> blocksToCatchUp = blockStore.getBlocksUntil(msg.receivedBlockHash, msg.lockedQC.blockHash);
       
        String message = "CATCH-UP_RESPONSE= ";

        CatchUp response = new CatchUp();
        response.viewNumber = curView;
        response.blockList = blocksToCatchUp;
        response.senderPort = memberConfig.getID() + 3000;

        String json = GsonUtils.GSON.toJson(response);
        message += json;
        
        try {
            networkLayerLib.alpSend(message, "localhost", msg.senderPort);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void handleCatchUpResponse(CatchUp msg){
        List<Block> blockList = msg.blockList;
        for (Block b : blockList) {
            try {
                if (blockStore.getBlockByHash(b.depHash()) == null) {
                    System.out.println("Storing catch-up block at height " + b.blockNumber + ": " + b.blockHash);
                    blockStore.storeBlock(b);
                }
            } catch (Exception e) {
                System.out.println("Error storing catch-up block: " + e.getMessage());
            }
        }

        if (!blockList.isEmpty()) {
            Block latestBlock = blockList.get(0);
            this.lastExecutedBlock = BlockchainMember.executeBlock(latestBlock, blockStore, lastExecutedBlock);
            System.out.println("Caught up to block height " + lastExecutedBlock.blockNumber);

            for (Transaction tx : latestBlock.transactions) {
                if (tx.senderPort >= 4000) {
                    memberConfig.setLastSequenceForClient(tx.senderPort, tx.nonce_count);
                    memberConfig.removePendingTransaction(tx);
                }
            }
        }
    }

    public Block getLastExecutedBlock() {
        return lastExecutedBlock;
    }

    private void sendBalanceResponse(GetBalance request, long balance, int senderPort) {
        GetBalance response = new GetBalance(
            request.getAddress(),
            request.getCoin(),
            null,
            balance,
            request.getSequenceNumber()
        );
        String responseJson = "GetBalanceResponse=" + GsonUtils.GSON.toJson(response);
        try {
            networkLayerLib.alpSend(responseJson, "localhost", senderPort);
        } catch (IOException e) {
            System.err.println("Error sending GetBalance response: " + e.getMessage());
        }
    }

    private void sendTransactionResponse(Transaction tx) {
        if (tx.senderPort < 4000) {
            return;
        }

        if (tx.executionSuccess == null) {
            System.err.println("Warning: Transaction has null executionSuccess, skipping response");
            return;
        }

        TransactionResponse response = new TransactionResponse(
            tx.executionSuccess,
            tx.getNonce()
        );

        String responseJson = "TransactionResponse=" + GsonUtils.GSON.toJson(response);
        try {
            networkLayerLib.alpSend(responseJson, "localhost", tx.senderPort);
        } catch (IOException e) {
            System.err.println("Error sending transaction response: " + e.getMessage());
        }
    }

}


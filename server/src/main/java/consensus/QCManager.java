package consensus;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import config.MemberConfig;
import crypto.CryptoLib;
import crypto.ThresholdSignatureService;
import model.Message;
import model.Node;
import model.QC;

public class QCManager {
    private final MemberConfig memberConfig;
    private final ThresholdSignatureService signatureService;

    // Vote storage: key = "type:viewNumber", value = list of votes
    private final ConcurrentHashMap<String, List<Message>> voteStore;

    public QCManager(MemberConfig memberConfig) {
        this.memberConfig = memberConfig;
        this.signatureService = new ThresholdSignatureService(
            memberConfig.getBlsPrivateKey(),
            memberConfig.getAllPublicKeys()
        );
        this.voteStore = new ConcurrentHashMap<>();
    }

    public boolean addVote(Message vote) {
        try {
            String key = createVoteKey(vote.type, vote.viewNumber);
            List<Message> votes = voteStore.computeIfAbsent(key, k -> new ArrayList<>());

            synchronized (votes) {
                for (Message existing : votes) {
                    if (existing.senderPort == vote.senderPort) {
                        return false;
                    }
                }
                votes.add(vote);
                return votes.size() >= memberConfig.getQuorumSize();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public QC formQC(String type, int viewNumber, Node node) throws Exception {
        String key = createVoteKey(type, viewNumber);
        List<Message> votes = voteStore.get(key);

        System.out.println("received this numebr of votes: " + (votes != null ? votes.size() : 0) + "\n"); //FIXME this is for testing, remove later
  
        if (votes == null || votes.size() < memberConfig.getQuorumSize()) {
            throw new IllegalStateException("Cannot form QC: insufficient votes");
        }

        
        // Collect partial signatures
        List<Integer> signerList = new ArrayList<>();
        List<byte[]> partialSigs = new ArrayList<>();
        synchronized (votes) {
            for (Message vote : votes) {
                if (vote.partialSig != null) {
                    int signerId = vote.senderPort - 3000;
                    System.out.println("[QC] Collecting signature from signer " + signerId + " (port " + vote.senderPort + ")");
                    partialSigs.add(vote.partialSig);
                    signerList.add(signerId); // Convert port back to member ID
                }
            }
        }
        System.out.println("[QC] Final signer list for " + type + ":" + viewNumber + " = " + signerList);

        byte[] messageHash = computeMessageHash(type, viewNumber, node);
        System.err.println("HASH OF THE FORMED QC " + type + ":" + viewNumber + ": " + Arrays.toString(messageHash));

        // Verify each partial signature individually before aggregation
        System.out.println("[QC] Verifying individual partial signatures:");
        for (int i = 0; i < signerList.size(); i++) {
            boolean valid = signatureService.verifyPartialSignature(partialSigs.get(i), messageHash, signerList.get(i));
            System.out.println("[QC]   Signer " + signerList.get(i) + ": " + (valid ? "VALID" : "INVALID!"));
        }

        byte[] aggregatedSig = signatureService.aggregateSignatures(partialSigs, messageHash);
        clearVotesForTypeView(type, viewNumber); // Clear old votes for new view FIXME is this good ? maybe 1 vote will get left behind
        QC qc = new QC(type, viewNumber, node, aggregatedSig);
        qc.signers = signerList;
        return qc;
    }

    public boolean verifyQC(QC qc) {
        try {
            if (qc.viewNumber == 0) {
                return true;
            }

            byte[] messageHash = computeMessageHash(qc.type, qc.viewNumber, qc.node);
            System.err.println("THIS IS THE HASH IN VERIFY FOR " + qc.type + ":" + qc.viewNumber + ": " + Arrays.toString(messageHash));
            return signatureService.verifyAggregatedSignature(
                qc.sig,
                messageHash,
                qc.signers
            );
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public byte[] createPartialSignature(String type, int viewNumber, Node node) throws Exception {
        byte[] messageHash = computeMessageHash(type, viewNumber, node);
        System.out.println("Creating partial signature for " + type + ":" + viewNumber + " with message hash: " + Arrays.toString(messageHash));
        return signatureService.createPartialSignature(memberConfig.getID(), messageHash);
    }

    public void clearVotesForTypeView(String type, int viewNumber) {
        voteStore.entrySet().removeIf(entry ->
            entry.getKey().contains(type + ":" + viewNumber)
        );
    }

    public List<Message> getVotes(String type, int viewNumber) {
        String key = createVoteKey(type, viewNumber);
        return voteStore.getOrDefault(key, new ArrayList<>());
    }

    private String createVoteKey(String type, int viewNumber) {
        return type + ":" + viewNumber;
    }

    private byte[] computeMessageHash(String type, int viewNumber, Node node) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(type.getBytes(StandardCharsets.UTF_8));
        baos.write(ByteBuffer.allocate(4).putInt(viewNumber).array());
        baos.write(node.depHash());
        return CryptoLib.hash(baos.toByteArray());
    }
}

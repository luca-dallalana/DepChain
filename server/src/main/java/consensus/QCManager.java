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
        List<byte[]> partialSigs = new ArrayList<>();
        synchronized (votes) {
            for (Message vote : votes) {
                if (vote.partialSig != null) {
                    partialSigs.add(vote.partialSig);
                }
            }
        }
        byte[] messageHash = computeMessageHash(type, viewNumber, node);
        byte[] aggregatedSig = signatureService.aggregateSignatures(partialSigs, messageHash);
        return new QC(type, viewNumber, node, aggregatedSig);
    }

    public boolean verifyQC(QC qc) {
        try {
            if (qc == null) {
                return false;
            }

            if (qc.viewNumber == 0) {
                return true;
            }

            byte[] messageHash = computeMessageHash(qc.type, qc.viewNumber, qc.node);
            return signatureService.verifyAggregatedSignature(
                qc.sig,
                messageHash,
                memberConfig.getQuorumSize()
            );
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public byte[] createPartialSignature(String type, int viewNumber, Node node) throws Exception {
        byte[] messageHash = computeMessageHash(type, viewNumber, node);
        return signatureService.createPartialSignature(memberConfig.getID(), messageHash);
    }

    public void clearVotesForView(int viewNumber) {
        voteStore.entrySet().removeIf(entry ->
            entry.getKey().contains(":" + viewNumber)
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

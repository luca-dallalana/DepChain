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
    private String genesisBlockHash;

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

    public void setGenesisBlockHash(String genesisBlockHash) {
        this.genesisBlockHash = genesisBlockHash;
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

                if (vote.type.equals("new-view")) {
                    if(!verifyQC(vote.justify)) {
                        System.out.println("Invalid justify QC in new-view vote from sender " + vote.senderPort);
                        return false;
                    }
                    
                } else {
                    byte[] messageHash = computeMessageHash(vote.type, vote.viewNumber, vote.blockHash);
                    if (!signatureService.verifyPartialSignature(vote.partialSig, messageHash, vote.senderPort - 3000)) {
                        System.out.println("Invalid partial signature from sender " + vote.senderPort);
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

    public QC formQC(String type, int viewNumber, String blockHash) throws Exception {
        String key = createVoteKey(type, viewNumber);
        List<Message> votes = voteStore.get(key);
  
        if (votes == null || votes.size() < memberConfig.getQuorumSize()) {
            throw new IllegalStateException("Cannot form QC: insufficient votes");
        }

        // Collect partial signatures
        List<Integer> signerList = new ArrayList<>();
        List<byte[]> partialSigs = new ArrayList<>();
        synchronized (votes) {
            for (Message vote : votes) {
                if (vote.partialSig != null) {
                    partialSigs.add(vote.partialSig);
                    signerList.add(vote.senderPort - 3000); // Convert port back to member ID
                }
            }
        }

        byte[] aggregatedSig = signatureService.aggregateSignatures(partialSigs);
        clearVotesForTypeView(type, viewNumber); // Clear old votes for new view
        QC qc = new QC(type, viewNumber, blockHash, aggregatedSig);
        qc.signers = signerList;
        return qc;
    }

    public boolean verifyQC(QC qc) {
        try {

           if (isGenesisQC(qc)) {
                return true;
            }

            if (qc.signers.size() < memberConfig.getQuorumSize()) {
                System.out.println("QC verification failed: insufficient signers");
                return false;
            }

            byte[] messageHash = computeMessageHash(qc.type, qc.viewNumber, qc.blockHash);
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

    private boolean isGenesisQC(QC qc) {
        if (qc == null) return false;
        if (!qc.type.equals("prepare")) return false;
        if (qc.viewNumber != 0) return false;
        if (qc.blockHash == null || qc.blockHash.isBlank()) return false;
        if (!genesisBlockHash.equals(qc.blockHash)) return false;
        boolean noSig = qc.sig == null || qc.sig.length == 0;
        boolean noSigners = qc.signers == null || qc.signers.isEmpty();
        return noSig && noSigners;
    }

    public byte[] createPartialSignature(String type, int viewNumber, String blockHash) throws Exception {
        byte[] messageHash = computeMessageHash(type, viewNumber, blockHash);
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

    private byte[] computeMessageHash(String type, int viewNumber, String blockHash) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(type.getBytes(StandardCharsets.UTF_8));
        baos.write(ByteBuffer.allocate(4).putInt(viewNumber).array());
        baos.write(blockHash.getBytes(StandardCharsets.UTF_8));
        return CryptoLib.hash(baos.toByteArray());
    }
}

package consensus;

import model.ClientRequest;
import config.MemberConfig;
import java.util.List;
import java.security.SecureRandom;
import java.security.PublicKey;

import java.util.Arrays;

import model.Node;
import model.QC;
import model.Message;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;

public class QCManagerTest {
    // Test: addVote should reject duplicate votes from the same sender
    @Test
    public void testAddVoteRejectsDuplicateSender() throws Exception {
        QCManager qcManager = new QCManager(dummyMemberConfig());
        Node node = new Node(new byte[32], dummyClientRequest(), 0);
        Message vote1 = new Message();
        vote1.type = "prepare";
        vote1.viewNumber = 0;
        vote1.node = node;
        vote1.partialSig = new byte[64];
        vote1.senderPort = 3000;
        // Accept first vote
        boolean first = qcManager.addVote(vote1);
        // Try to add duplicate vote from same sender
        Message vote2 = new Message();
        vote2.type = "prepare";
        vote2.viewNumber = 0;
        vote2.node = node;
        vote2.partialSig = new byte[64];
        vote2.senderPort = 3000;
        boolean second = qcManager.addVote(vote2);
        if (!second) {
            System.out.println("-----------------------------------");
            System.out.println("addVote: duplicate sender rejected");
            System.out.println("-----------------------------------");
        }
        assertFalse(second, "addVote should reject duplicate sender votes");
    }

    // Test: addVote should reject vote with invalid partial signature
    @Test
    public void testAddVoteRejectsInvalidPartialSignature() throws Exception {
        QCManager qcManager = new QCManager(dummyMemberConfig());
        Node node = new Node(new byte[32], dummyClientRequest(), 0);
        Message vote = new Message();
        vote.type = "prepare";
        vote.viewNumber = 0;
        vote.node = node;
        vote.partialSig = new byte[64];
        vote.senderPort = 3001;
        boolean result = qcManager.addVote(vote);
        if (!result) {
            System.out.println("-------------------------------------------");
            System.out.println("addVote: invalid partial signature rejected");
            System.out.println("-------------------------------------------");
        } 
        assertFalse(result, "addVote should reject invalid partial signature");
    }

    // Test: addVote should reject new-view vote with invalid justify QC
    @Test
    public void testAddVoteRejectsInvalidJustifyQC() throws Exception {
        QCManager qcManager = new QCManager(dummyMemberConfig());
        Node node = new Node(new byte[32], dummyClientRequest(), 0);
        Message vote = new Message();
        vote.type = "new-view";
        vote.viewNumber = 0;
        vote.node = node;
        vote.partialSig = new byte[64];
        vote.senderPort = 3002;
        QC invalidQC = new QC("prepare", 0, node, null); // no sig, not enough signers
        invalidQC.signers = Collections.singletonList(0);
        vote.justify = invalidQC;
        boolean result = qcManager.addVote(vote);
        if (!result) {
            System.out.println("-----------------------------------------------------");
            System.out.println("addVote: invalid justify QC rejected in new-view vote");
            System.out.println("-----------------------------------------------------");
        }
        assertFalse(result, "addVote should reject new-view vote with invalid justify QC");
    }
    // Helper to generate random bytes for keys
    private static byte[] randomBytes(int len) {
        byte[] b = new byte[len];
        new SecureRandom().nextBytes(b);
        return b;
    }
    private static ClientRequest dummyClientRequest() {
        return new ClientRequest(0, "dummy", new byte[0]);
    }

    private static MemberConfig dummyMemberConfig() {
        int N = 4;
        int thisID = 0;
        PublicKey dummyPubKey = null;
        MemberConfig config = new MemberConfig(N, thisID, dummyPubKey);

        byte[] privKey = new byte[32];
        privKey[31] = 1;

        List<byte[]> pubKeys = List.of(new byte[48], new byte[48], new byte[48], new byte[48]);
        config.initializeBLSKeys(privKey, pubKeys);
        return config;
    }
    @Test
    public void testQCWithNoSignature() {
        Node fakeNode = new Node(new byte[32], dummyClientRequest(), 0);
        QC qc = new QC("prepare", 1, fakeNode, null); // No signature
        qc.signers = Collections.singletonList(0);
        QCManager qcManager = new QCManager(dummyMemberConfig());
        boolean result = qcManager.verifyQC(qc);
        if(!result){
            System.out.println("-------------------------------------------");
            System.out.println("verifyQC: QC has no signature, rejected!!!!");
            System.out.println("-------------------------------------------");
        }
        assertFalse(result, "QC with no signature should not verify");
    }

    @Test
    public void testQCWithWrongNumberOfSigners() {
        Node fakeNode = new Node(new byte[32], dummyClientRequest(), 0);
        QC qc = new QC("prepare", 1, fakeNode, new byte[64]);
        qc.signers = Arrays.asList(0); // Only one signer
        QCManager qcManager = new QCManager(dummyMemberConfig());
        boolean result = qcManager.verifyQC(qc);
        if(!result){
            System.out.println("----------------------------------------------");
            System.out.println("verifyQC: QC has too few signers, rejected!!!!");
            System.out.println("----------------------------------------------");
        }
        assertFalse(result, "QC with too few signers should not verify");
    }

    @Test
    public void testQCWithWrongSignature() {
        Node fakeNode = new Node(new byte[32], dummyClientRequest(), 0);
        QC qc = new QC("prepare", 1, fakeNode, new byte[64]); // wrong signature
        qc.signers = Arrays.asList(0, 1, 2);
        QCManager qcManager = new QCManager(dummyMemberConfig());
        boolean result = qcManager.verifyQC(qc);
        if(!result){
            System.out.println("----------------------------------------------");
            System.out.println("verifyQC: QC has wrong signature, rejected!!!!");
            System.out.println("----------------------------------------------");
        }
        assertFalse(result, "QC with wrong signature should not verify");
    }
}

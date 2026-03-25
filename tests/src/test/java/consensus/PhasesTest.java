package consensus;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import blockchain.Transaction;
import member.DepChainMember;
import config.MemberConfig;
import model.QC;
import model.Message;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PhasesTest {

    // Test: handlePrepareReplica should reject message with null justify
    @Test
    public void testHandlePrepareReplicaRejectsNullJustify() throws Exception {
        Message m = new Message();
        m.type = "prepare";
        m.viewNumber = 0;
        m.blockHash = "dummy-block-hash";
        m.justify = null;
        m.transactions = Collections.emptyList();
        m.senderPort = 3000; // leader for view 0
    
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));
        try {
            invokeHandlePrepareReplica(m);
            String output = errContent.toString();

            assertTrue(output.contains("Null new-view maxQC in prepare message"), "Output should contain: Null new-view maxQC in prepare message");
        } finally {
            System.setErr(originalErr);
        }
        System.out.println("---------------------------------------------");
        System.out.println("handlePrepareReplica: null justify rejected");
        System.out.println("---------------------------------------------");
    }
    
    // Test: handlePrepareReplica should reject message with invalid signature
    @Test
    public void testHandlePrepareReplicaRejectsInvalidSignature() throws Exception {
        byte[] fakeSig = new byte[256];
        Transaction tx = new Transaction(4001, null, null, 1, new byte[0], 21000, 1, 1, fakeSig);

        Message m = new Message();
        m.type = "prepare";
        m.viewNumber = 0;
        m.transactions = List.of(tx);
        m.blockHash = "dummy-block-hash";
        m.justify = new QC("prepare", 0, "genesis-hash", null);
        m.senderPort = 3000; // leader for view 0
    
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));
        try {
            invokeHandlePrepareReplica(m);
            String output = errContent.toString();

            assertTrue(output.contains("Invalid signature for client transaction in block, proposing new view"), "Output should contain: Invalid signature for client transaction in block, proposing new view");
        } finally {
            System.setErr(originalErr);
        }
    
        
        System.out.println("---------------------------------------------");
        System.out.println("handlePrepareReplica: invalid signature rejected");
        System.out.println("---------------------------------------------");
    }
    // Test: handlePrepareReplica should reject message with invalid sender (not from leader)
    @Test
    public void testHandlePrepareReplicaRejectsInvalidSender() throws Exception {
        Message m = new Message();
        m.type = "prepare";
        m.viewNumber = 1;
        m.transactions = Collections.emptyList();
        m.blockHash = "dummy-block-hash";
        m.justify = new QC("prepare", 1, "some-parent-hash", null);
        m.justify.signers = Arrays.asList(0, 1, 2);
        m.senderPort = 3002; // not the leader for view 1
        
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));
        try {
            invokeHandlePrepareReplica(m);
            String output = errContent.toString();
            assertTrue(output.contains("Invalid late message or sender in prepare message"), "Output should contain: Invalid late message or sender in prepare message");
        } finally {
            System.setErr(originalErr);
        }
        System.out.println("---------------------------------------------");
        System.out.println("handlePrepareReplica: invalid sender rejected");
        System.out.println("---------------------------------------------");
    }

    // Test: handlePrepareReplica should reject message with invalid justify QC (when curView != 0)
    @Test
    public void testHandlePrepareReplicaRejectsInvalidJustifyQC() throws Exception {
        // Set curView to 1
        java.lang.reflect.Field curViewField = DepChainMember.class.getDeclaredField("curView");
        curViewField.setAccessible(true);
        curViewField.setInt(member, 1);

        Message m = new Message();
        m.type = "prepare";
        m.viewNumber = 1;
        m.transactions = Collections.emptyList();
        m.blockHash = "dummy-block-hash";
        m.justify = new QC("prepare", 1, "some-parent-hash", null); // invalid QC (no valid signature)
        m.justify.signers = Arrays.asList(0, 1, 2);
        m.senderPort = 3001; // leader for view 1

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));
        try {
            invokeHandlePrepareReplica(m);
            String output = errContent.toString();
            assertTrue(output.contains("Invalid justify QC in prepare message"), "Output should contain: Invalid justify QC in prepare message");
        } finally {
            System.setErr(originalErr);
        }

        System.out.println("--------------------------------------------------");
        System.out.println("handlePrepareReplica: invalid justify QC rejected");
        System.out.println("--------------------------------------------------");
    }
    // Test: handlePreCommitReplica should reject message with QC of wrong type
    @Test
    public void testHandlePreCommitReplicaRejectsWrongTypeQC() throws Exception {
        Message m = new Message();
        m.type = "prepare";
        m.viewNumber = 0;
        m.transactions = Collections.emptyList();
        m.blockHash = "dummy-block-hash";
        m.justify = new QC("commit", 0, "some-parent-hash", null); // wrong type QC
        m.senderPort = 3000; // leader for view 0
        m.justify.signers = Arrays.asList(0, 1, 2);

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));

        try {

            invokeHandlePreCommitReplica(m);

            String output = errContent.toString();
            assertTrue(output.contains("Invalid late prepareQC or sender in pre-commit message"), "Output should contain: Invalid late prepareQC or sender in pre-commit message");
        } finally {
            // Always restore System.err
            System.setErr(originalErr);
        }

        System.out.println("----------------------------------------------");
        System.out.println("handlePreCommitReplica: wrong type QC rejected");
        System.out.println("----------------------------------------------");
    }
    
    // Test: handlePreCommitReplica should reject message with null justify
    @Test
    public void testHandlePreCommitReplicaRejectsNullJustify() throws Exception {
        Message m = new Message();
        m.type = "pre-commit";
        m.viewNumber = 0;
        m.blockHash = "dummy-block-hash";
        m.justify = null;
        m.transactions = Collections.emptyList();
        m.senderPort = 3000; // leader for view 0
        
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));
        try {
            invokeHandlePreCommitReplica(m);
            String output = errContent.toString();
            
            assertTrue(output.contains("Null prepareQC in pre-commit message"), "Output should contain: Null prepareQC in pre-commit message");
        } finally {
            System.setErr(originalErr);
        }
        System.out.println("---------------------------------------------");
        System.out.println("handlePreCommitReplica: null justify rejected");
        System.out.println("---------------------------------------------");
    }
    
    // Test: handlePreCommitReplica should reject message with invalid sender
    @Test
    public void testHandlePreCommitReplicaRejectsInvalidSender() throws Exception {
        Message m = new Message();
        m.type = "pre-commit";
        m.viewNumber = 0;
        m.transactions = Collections.emptyList();
        m.blockHash = "dummy-block-hash";
        m.justify = new QC("prepare", 0, "some-parent-hash", null);
        m.justify.signers = Arrays.asList(0, 1, 2);
        m.senderPort = 3002; // not the leader for view 0
        
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));
        try {
            invokeHandlePreCommitReplica(m);
            String output = errContent.toString();
            assertTrue(output.contains("Invalid late prepareQC or sender in pre-commit message"), "Output should contain: Invalid late prepareQC or sender in pre-commit message");
        } finally {
            System.setErr(originalErr);
        }
        System.out.println("-----------------------------------------------");
        System.out.println("handlePreCommitReplica: invalid sender rejected");
        System.out.println("-----------------------------------------------");
    }
    
    // Test: handlePreCommitReplica should reject message with invalid justify QC
    @Test
    public void testHandlePreCommitReplicaRejectsInvalidJustifyQC() throws Exception {
        Message m = new Message();
        m.type = "pre-commit";
        m.viewNumber = 0;
        m.transactions = Collections.emptyList();
        m.blockHash = "dummy-block-hash";
        m.justify = new QC("prepare", 1, "some-parent-hash", null); // invalid QC (no valid signature)
        m.justify.signers = Arrays.asList(0, 1, 2);
        m.senderPort = 3000; // leader for view 0
        
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));
        try {
            invokeHandlePreCommitReplica(m);
            String output = errContent.toString();
            
            assertTrue(output.contains("Invalid prepareQC in pre-commit message"), "Output should contain: Invalid prepareQC in pre-commit message");
        } finally {
            System.setErr(originalErr);
        }
        System.out.println("---------------------------------------------------");
        System.out.println("handlePreCommitReplica: invalid justify QC rejected");
        System.out.println("---------------------------------------------------");
    }
    
    // Test: handleCommitReplica should reject message with QC of wrong type
    @Test
    public void testHandleCommitReplicaRejectsWrongTypeQC() throws Exception {
        Message m = new Message();
        m.type = "pre-commit";
        m.viewNumber = 0;
        m.transactions = Collections.emptyList();
        m.blockHash = "dummy-block-hash";
        m.justify = new QC("prepare", 0, "some-parent-hash", null); // wrong type QC
        m.justify.signers = Arrays.asList(0, 1, 2);
        m.senderPort = 3000; // leader for view 0
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));
        try {

            invokeHandleCommitReplica(m);

            String output = errContent.toString();
            assertTrue(output.contains("Invalid late precommitQC or sender in commit message"), "Output should contain: Invalid late precommitQC or sender in commit message");
        } finally {
            // Always restore System.err
            System.setErr(originalErr);
        }

        System.out.println("-------------------------------------------");
        System.out.println("handleCommitReplica: wrong type QC rejected");
        System.out.println("-------------------------------------------");
    }
    // Test: handleCommitReplica should reject message with null justify
    @Test
    public void testHandleCommitReplicaRejectsNullJustify() throws Exception {
        Message m = new Message();
        m.type = "commit";
        m.viewNumber = 0;
        m.blockHash = "dummy-block-hash";
        m.justify = null;
        m.transactions = Collections.emptyList();
        m.senderPort = 3000; // leader for view 0

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));
        try {
            invokeHandleCommitReplica(m);
            String output = errContent.toString();

            assertTrue(output.contains("Null precommitQC in commit message"), "Output should contain: Null precommitQC in commit message");
        } finally {
            System.setErr(originalErr);
        }

        System.out.println("-------------------------------------------");
        System.out.println("handleCommitReplica: null justify rejected");
        System.out.println("-------------------------------------------");
    }

    // Test: handleCommitReplica should reject message with invalid sender
    @Test
    public void testHandleCommitReplicaRejectsInvalidSender() throws Exception {
        Message m = new Message();
        m.type = "commit";
        m.viewNumber = 0;
        m.transactions = Collections.emptyList();
        m.blockHash = "dummy-block-hash";
        m.justify = new QC("pre-commit", 0, "some-parent-hash", null);
        m.justify.signers = Arrays.asList(0, 1, 2);
        m.senderPort = 3002; // not the leader

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));
        try {
            invokeHandleCommitReplica(m);
            String output = errContent.toString();
            assertTrue(output.contains("Invalid late precommitQC or sender in commit message"), "Output should contain: Invalid late precommitQC or sender in commit message");
        } finally {
            System.setErr(originalErr);
        }

        System.out.println("--------------------------------------------");
        System.out.println("handleCommitReplica: invalid sender rejected");
        System.out.println("--------------------------------------------");
    }

    // Test: handleCommitReplica should reject message with invalid justify QC
    @Test
    public void testHandleCommitReplicaRejectsInvalidJustifyQC() throws Exception {
        Message m = new Message();
        m.type = "commit";
        m.viewNumber = 0;
        m.transactions = Collections.emptyList();
        m.blockHash = "dummy-block-hash";
        m.justify = new QC("pre-commit", 1, "some-parent-hash", null); // invalid QC (no valid signature)
        m.justify.signers = Arrays.asList(0, 1, 2);
        m.senderPort = 3000; // leader for view 0

        PrintStream originalErr = System.err;
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errContent));
        try {
            invokeHandleCommitReplica(m);
            String output = errContent.toString();

            assertTrue(output.contains("Invalid precommitQC in commit message"), "Output should contain: Invalid precommitQC in commit message");
        } finally {
            System.setErr(originalErr);
        }

        System.out.println("------------------------------------------------");
        System.out.println("handleCommitReplica: invalid justify QC rejected");
        System.out.println("------------------------------------------------");
    }
    // Helper to invoke private handlePreCommitReplica
    private void invokeHandlePreCommitReplica(Message m) throws Exception {
        java.lang.reflect.Method method = DepChainMember.class.getDeclaredMethod("handlePreCommitReplica", Message.class);
        method.setAccessible(true);
        method.invoke(member, m);
    }

    // Helper to invoke private handleCommitReplica
    private void invokeHandleCommitReplica(Message m) throws Exception {
        java.lang.reflect.Method method = DepChainMember.class.getDeclaredMethod("handleCommitReplica", Message.class);
        method.setAccessible(true);
        method.invoke(member, m);
    }
    private DepChainMember member;
    private MemberConfig config;
    private DatagramSocket socket;

    @BeforeEach
    public void setUp() throws Exception {
        config = dummyMemberConfig();
        socket = new DatagramSocket();
        member = new DepChainMember(config, socket);
    }

    @AfterEach
    public void tearDown() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    private static MemberConfig dummyMemberConfig() {
        int N = 4;
        int thisID = 0;
        java.security.PublicKey dummyPubKey = null;
        MemberConfig config = new MemberConfig(N, thisID, dummyPubKey);
        byte[] privKey = new byte[32];
        privKey[31] = 1;
        java.util.List<byte[]> pubKeys = java.util.List.of(new byte[48], new byte[48], new byte[48], new byte[48]);
        config.initializeBLSKeys(privKey, pubKeys);
        return config;
    }

    // Helper to invoke private handlePrepareReplica
    private void invokeHandlePrepareReplica(Message m) throws Exception {
        java.lang.reflect.Method method = DepChainMember.class.getDeclaredMethod("handlePrepareReplica", Message.class);
        method.setAccessible(true);
        method.invoke(member, m);
    }
}

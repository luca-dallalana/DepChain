package member;

import static org.junit.jupiter.api.Assertions.*;

import model.ClientRequest;
import model.Node;
import model.QC;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import config.MemberConfig;
import java.net.DatagramSocket;

public class SafeNodeTest {
    private DepChainMember member;
    private MemberConfig dummyConfig;
    private java.net.DatagramSocket dummySocket;

    @BeforeEach
    public void setUp() throws Exception {
        dummyConfig = dummyMemberConfig();
        dummySocket = new java.net.DatagramSocket(); 
        member = new DepChainMember(dummyConfig, dummySocket);
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

    // Test: When lockedQC is null, safeNode should return true if node extends from qc.node (descendant case)
    @Test
    public void testSafeNode_LockedQCNull_ExtendsFrom() throws Exception {

        Node parent = getFirstNode(member); 
        ClientRequest cmd = new ClientRequest(1, 4001, "cmd", new byte[]{1,2,3});
        Node child = Node.createLeaf(parent, cmd);
        storeNode(member, child);
        QC qc = new QC("prepare", 0, parent, null);

        boolean result = member.safeNode(child, qc);

        if (result) {
            System.out.println("---------------------------------------------------------------------");
            System.out.println("safeNode: safe because lockedQC is null and node extends from qc.node");
            System.out.println("---------------------------------------------------------------------");
        } 
        assertTrue(result, "safeNode should return true when lockedQC is null and node extends from qc.node");
    }

    // Test: When lockedQC is set, safeNode should return true if node extends from lockedQC.node (safety rule)
    @Test
    public void testSafeNode_LockedQCNotNull_ExtendsFrom() throws Exception {

        Node parent = getFirstNode(member); 
        ClientRequest cmd = new ClientRequest(1, 4001, "cmd", new byte[]{1,2,3});
        Node child = Node.createLeaf(parent, cmd);
        storeNode(member, child);
        QC lockedQC = new QC("pre-commit", 0, parent, null);
        QC qc = new QC("prepare", 0, parent, null);
        setLockedQC(member, lockedQC);

        boolean result = member.safeNode(child, qc);

        if (result) {
            System.out.println("------------------------------------------------------");
            System.out.println("safeNode: safe because node extends from lockedQC.node");
            System.out.println("------------------------------------------------------");
        }
        assertTrue(result, "safeNode should return true when node extends from lockedQC.node");
    }

    // Test: When lockedQC is set, safeNode should return true if qc.viewNumber > lockedQC.viewNumber (liveness rule)
    @Test
    public void testSafeNode_LockedQCNotNull_HigherView() throws Exception {

        Node parent = getFirstNode(member); 
        ClientRequest cmd = new ClientRequest(1, 4001, "cmd", new byte[]{1,2,3});
        Node child = Node.createLeaf(parent, cmd);
        storeNode(member, child);
        QC lockedQC = new QC("pre-commit", 0, parent, null);
        QC qc = new QC("prepare", 5, parent, null); // higher view
        setLockedQC(member, lockedQC);

        boolean result = member.safeNode(child, qc);

        if (result) {
            System.out.println("----------------------------------------------------------");
            System.out.println("safeNode: safe because qc.viewNumber > lockedQC.viewNumber");
            System.out.println("----------------------------------------------------------");
        }
        assertTrue(result, "safeNode should return true when qc.viewNumber > lockedQC.viewNumber");
    }
    // Test: When lockedQC is set, safeNode should return false if node does not extend from lockedQC.node and qc.viewNumber <= lockedQC.viewNumber (unsafe case)
    @Test
    public void testSafeNode_LockedQCNotNull_FalseCase() throws Exception {

        Node parent = getFirstNode(member); 
        ClientRequest cmd = new ClientRequest(1, 4001, "cmd", new byte[]{1,2,3});
        Node unrelated = new Node(null, new ClientRequest(1, 0, "", null), 0);
        storeNode(member, unrelated);
        QC lockedQC = new QC("pre-commit", 5, parent, null);
        QC qc = new QC("prepare", 2, unrelated, null); // lower view, unrelated node
        setLockedQC(member, lockedQC);

        boolean result = member.safeNode(unrelated, qc);

        if (!result) {
            System.out.println("--------------------------------------------------------------------------------------------------------");
            System.out.println("safeNode: not safe because it doesn't extend from lockedQC.node and qc.viewNumber <= lockedQC.viewNumber");
            System.out.println("--------------------------------------------------------------------------------------------------------");
        } 

        assertFalse(result, "safeNode should return false when node does not extend from lockedQC.node and qc.viewNumber <= lockedQC.viewNumber");
    }

    private Node getFirstNode(DepChainMember member) throws Exception {
        java.lang.reflect.Field field = DepChainMember.class.getDeclaredField("nodeTree");
        field.setAccessible(true);
        model.NodeTree nodeTree = (model.NodeTree) field.get(member);
        return nodeTree.getFirstNode();
    }


    private void setLockedQC(DepChainMember member, QC lockedQC) {
        try {
            java.lang.reflect.Field field = DepChainMember.class.getDeclaredField("lockedQC");
            field.setAccessible(true);
            field.set(member, lockedQC);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void storeNode(DepChainMember member, Node node) throws Exception {
        java.lang.reflect.Field field = DepChainMember.class.getDeclaredField("nodeTree");
        field.setAccessible(true);
        model.NodeTree nodeTree = (model.NodeTree) field.get(member);
        nodeTree.storeNode(node);
    }
}

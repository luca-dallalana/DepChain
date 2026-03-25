package consensus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.DatagramSocket;
import java.util.ArrayList;

import blockchain.Block;
import blockchain.BlockStore;
import blockchain.WorldState;
import config.MemberConfig;
import member.DepChainMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import model.QC;

public class SafeNodeTest {
    private DepChainMember member;
    private MemberConfig dummyConfig;
    private DatagramSocket dummySocket;
    private Block genesis;

    @BeforeEach
    public void setUp() throws Exception {
        dummyConfig = dummyMemberConfig();
        dummySocket = new DatagramSocket();
        member = new DepChainMember(dummyConfig, dummySocket);
        genesis = initializeBlockStore(member);
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

    // Test: When lockedQC is null, safeBlock should return true if block extends from qc.blockHash.
    @Test
    public void testSafeBlock_LockedQCNull_ExtendsFrom() throws Exception {
        Block child = Block.createLeaf(genesis, new ArrayList<>(), new WorldState());
        storeBlock(member, child);
        QC qc = new QC("prepare", 0, genesis.blockHash, null);

        boolean result = member.safeBlock(child, qc);

        assertTrue(result, "safeBlock should return true when lockedQC is null and block extends from qc.blockHash");
    }

    // Test: When lockedQC is set, safeBlock should return true if block extends from lockedQC.blockHash.
    @Test
    public void testSafeBlock_LockedQCNotNull_ExtendsFrom() throws Exception {
        Block child = Block.createLeaf(genesis, new ArrayList<>(), new WorldState());
        storeBlock(member, child);
        QC lockedQC = new QC("pre-commit", 0, genesis.blockHash, null);
        QC qc = new QC("prepare", 0, genesis.blockHash, null);
        setLockedQC(member, lockedQC);

        boolean result = member.safeBlock(child, qc);

        assertTrue(result, "safeBlock should return true when block extends from lockedQC.blockHash");
    }

    // Test: When lockedQC is set, safeBlock should return true if qc.viewNumber > lockedQC.viewNumber.
    @Test
    public void testSafeBlock_LockedQCNotNull_HigherView() throws Exception {
        Block unrelated = new Block(null, null, new ArrayList<>(), new WorldState(), 10);
        unrelated.blockHash = unrelated.depHash();
        storeBlock(member, unrelated);

        QC lockedQC = new QC("pre-commit", 5, genesis.blockHash, null);
        QC qc = new QC("prepare", 6, unrelated.blockHash, null); // higher view
        setLockedQC(member, lockedQC);

        boolean result = member.safeBlock(unrelated, qc);

        assertTrue(result, "safeBlock should return true when qc.viewNumber > lockedQC.viewNumber");
    }

    // Test: When lockedQC is set, safeBlock should return false if it does not extend and view is not higher.
    @Test
    public void testSafeBlock_LockedQCNotNull_FalseCase() throws Exception {
        Block unrelated = new Block(null, null, new ArrayList<>(), new WorldState(), 20);
        unrelated.blockHash = unrelated.depHash();
        storeBlock(member, unrelated);

        QC lockedQC = new QC("pre-commit", 5, genesis.blockHash, null);
        QC qc = new QC("prepare", 2, unrelated.blockHash, null); // lower view, unrelated block
        setLockedQC(member, lockedQC);

        boolean result = member.safeBlock(unrelated, qc);

        assertFalse(result, "safeBlock should return false when block does not extend from lockedQC.blockHash and qc.viewNumber <= lockedQC.viewNumber");
    }

    private Block initializeBlockStore(DepChainMember member) throws Exception {
        Block first = new Block(null, null, new ArrayList<>(), new WorldState(), 0);
        BlockStore store = new BlockStore(first);

        java.lang.reflect.Field field = DepChainMember.class.getDeclaredField("blockStore");
        field.setAccessible(true);
        field.set(member, store);
        return store.getFirstBlock();
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

    private void storeBlock(DepChainMember member, Block block) throws Exception {
        java.lang.reflect.Field field = DepChainMember.class.getDeclaredField("blockStore");
        field.setAccessible(true);
        BlockStore blockStore = (BlockStore) field.get(member);
        blockStore.storeBlock(block);
    }
}

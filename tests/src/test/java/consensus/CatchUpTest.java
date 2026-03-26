package consensus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import blockchain.Block;
import blockchain.BlockStore;
import blockchain.BlockchainMember;
import blockchain.WorldState;
import config.MemberConfig;
import member.DepChainMember;
import model.CatchUp;
import model.QC;
import network.GsonUtils;
import network.NetworkLayerLib;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CatchUpTest {

    private DatagramSocket socket0;
    private DatagramSocket socket1;

    private DepChainMember node0;
    private DepChainMember node1;

    private BlockStore store0;
    private BlockStore store1;

    @BeforeEach
    public void setUp() throws Exception {
        MemberConfig config0 = dummyMemberConfig(2, 0);
        MemberConfig config1 = dummyMemberConfig(2, 1);

        socket0 = new DatagramSocket();
        socket1 = new DatagramSocket();

        node0 = new DepChainMember(config0, socket0);
        node1 = new DepChainMember(config1, socket1);

        Block genesis = new Block(null, null, new ArrayList<>(), new WorldState(), 0);
        genesis.blockHash = genesis.depHash();

        store0 = new BlockStore(genesis);
        store1 = new BlockStore(genesis);

        setField(node0, "blockStore", store0);
        setField(node1, "blockStore", store1);

        QC genesisQC0 = new QC("prepare", 0, genesis.blockHash, null);
        genesisQC0.signers = new ArrayList<>();
        QC genesisQC1 = new QC("prepare", 0, genesis.blockHash, null);
        genesisQC1.signers = new ArrayList<>();

        setField(node0, "lockedQC", genesisQC0);
        setField(node1, "lockedQC", genesisQC1);

        QCManager qcManager0 = (QCManager) getField(node0, "qcManager");
        QCManager qcManager1 = (QCManager) getField(node1, "qcManager");
        qcManager0.setGenesisBlockHash(genesis.blockHash);
        qcManager1.setGenesisBlockHash(genesis.blockHash);

        InMemoryRouter router = new InMemoryRouter();
        router.register(3000, node0);
        router.register(3001, node1);

        node0.setNetworkLayerLib(new InMemoryNetworkLayer(node0, socket0, 3000, router));
        node1.setNetworkLayerLib(new InMemoryNetworkLayer(node1, socket1, 3001, router));
    }

    @AfterEach
    public void tearDown() {
        if (socket0 != null && !socket0.isClosed()) {
            socket0.close();
        }
        if (socket1 != null && !socket1.isClosed()) {
            socket1.close();
        }
    }

    @Test
    public void testCatchUpEndToEndBetweenTwoNodes() throws Exception {
        Block genesis0 = store0.getFirstBlock();

        Block block1 = BlockchainMember.buildBlock(genesis0, new ArrayList<>());
        Block block2 = BlockchainMember.buildBlock(block1, new ArrayList<>());
        Block block3 = BlockchainMember.buildBlock(block2, new ArrayList<>());
        Block block4 = BlockchainMember.buildBlock(block3, new ArrayList<>());

        store0.storeBlock(block1);
        store0.storeBlock(block2);
        store0.storeBlock(block3);
        store0.storeBlock(block4);

        // Node 1 already has history until block2 and only needs block3 + block4.
        store1.storeBlock(block1);
        store1.storeBlock(block2);

        QC node1LockedQc = new QC("prepare", 0, block2.blockHash, null);
        node1LockedQc.signers = new ArrayList<>();
        setField(node1, "lockedQC", node1LockedQc);

        // Keep catch-up request verifiable with the current QCManager genesis-QC rule.
        QCManager qcManager0 = (QCManager) getField(node0, "qcManager");
        qcManager0.setGenesisBlockHash(block2.blockHash);

        CatchUp request = new CatchUp();
        request.viewNumber = 0;
        request.lockedQC = new QC("prepare", 0, block2.blockHash, null);
        request.lockedQC.signers = new ArrayList<>();
        request.receivedBlockHash = block4.blockHash;
        request.senderPort = 3001;

        String payload = "CATCH-UP=" + GsonUtils.GSON.toJson(request);

        node0.onDeliver(3001, payload);

        Block node1Block3 = store1.getBlockByHash(block3.blockHash);
        Block node1Block4 = store1.getBlockByHash(block4.blockHash);

        assertNotNull(node1Block3, "Node 1 should store first missing block from catch-up response");
        assertNotNull(node1Block4, "Node 1 should store second missing block from catch-up response");
        assertEquals(block2.blockHash, node1Block3.parentBlockHash, "First caught-up block should extend node1 locked block");
        assertEquals(node1Block3.blockHash, node1Block4.parentBlockHash, "Second caught-up block should extend first caught-up block");
    }

    private static MemberConfig dummyMemberConfig(int n, int thisId) {
        java.security.PublicKey dummyPubKey = null;
        MemberConfig config = new MemberConfig(n, thisId, dummyPubKey);

        byte[] privKey = new byte[32];
        privKey[31] = 1;

        List<byte[]> pubKeys = List.of(new byte[48], new byte[48]);
        config.initializeBLSKeys(privKey, pubKeys);

        return config;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(target);
    }

    private static class InMemoryRouter {
        private final Map<Integer, DepChainMember> membersByPort = new HashMap<>();

        void register(int port, DepChainMember member) {
            membersByPort.put(port, member);
        }

        void send(int sourcePort, int destPort, String payload) {
            DepChainMember target = membersByPort.get(destPort);
            if (target != null) {
                target.onDeliver(sourcePort, payload);
            }
        }
    }

    private static class InMemoryNetworkLayer extends NetworkLayerLib {
        private final int sourcePort;
        private final InMemoryRouter router;

        InMemoryNetworkLayer(DepChainMember listener, DatagramSocket socket, int sourcePort, InMemoryRouter router) {
            super(listener, socket);
            this.sourcePort = sourcePort;
            this.router = router;
        }

        @Override
        public void alpSend(String m, String dest, Integer port) throws IOException {
            router.send(sourcePort, port, m);
        }
    }
}

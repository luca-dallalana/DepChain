package blockchain;

import blockchain.evm.ABIEncoder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import network.GsonUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rpc.JsonRpcServer;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JsonRpcTest {

    private static final int PORT = 18545;
    private static final String BASE_URL = "http://localhost:" + PORT;

    private static JsonRpcServer rpcServer;
    private static Block genesis;
    private static HttpClient httpClient;
    private static String client0Addr;

    @BeforeAll
    static void startServer() throws Exception {
        try {
            Files.deleteIfExists(Paths.get("../blockchain_data/genesis_block.json"));
        } catch (Exception ignored) {}

        genesis = Block.createAndSaveGenesis("..");
        client0Addr = AddressUtils.generateAddressFromPublicKey("../rsa_keys/client_0/client_0.pubkey");

        Map<String, Block> blockMap = Map.of(genesis.blockHash, genesis);
        rpcServer = new JsonRpcServer(PORT, () -> genesis, tx -> {}, blockMap::get);
        rpcServer.start();

        httpClient = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stopServer() {
        if (rpcServer != null) rpcServer.stop();
    }

    private JsonObject sendRpc(String method, List<Object> params) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("jsonrpc", "2.0");
        req.put("method", method);
        req.put("params", params != null ? params : List.of());
        req.put("id", 1);
        String body = GsonUtils.GSON.toJson(req);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    @Test
    public void testEthBlockNumber() throws Exception {
        JsonObject resp = sendRpc("eth_blockNumber", List.of());
        assertEquals("0x0", resp.get("result").getAsString());
    }

    @Test
    public void testEthChainId() throws Exception {
        JsonObject resp = sendRpc("eth_chainId", List.of());
        assertEquals("0x539", resp.get("result").getAsString());
    }

    @Test
    public void testEthGetBalance() throws Exception {
        JsonObject resp = sendRpc("eth_getBalance", List.of(Block.ADMIN_ADDRESS, "latest"));
        String balance = resp.get("result").getAsString();
        assertNotNull(balance);
        assertNotEquals("0x0", balance, "Admin DepCoin balance should be non-zero");
    }

    @Test
    public void testEthGetTransactionCount() throws Exception {
        JsonObject resp = sendRpc("eth_getTransactionCount", List.of(client0Addr, "latest"));
        assertEquals("0x0", resp.get("result").getAsString(), "client0 nonce should be 0 in genesis");
    }

    @Test
    public void testEthGetBlockByHash() throws Exception {
        JsonObject resp = sendRpc("eth_getBlockByHash", List.of(genesis.blockHash, false));
        JsonObject block = resp.get("result").getAsJsonObject();
        assertEquals("0x0", block.get("blockNumber").getAsString());
    }

    @Test
    public void testDepGetLatestBlock() throws Exception {
        JsonObject resp = sendRpc("dep_getLatestBlock", List.of());
        JsonObject block = resp.get("result").getAsJsonObject();
        assertEquals("0x0", block.get("blockNumber").getAsString());
    }

    @Test
    public void testEthCall() throws Exception {
        Address client0 = Address.fromHexString(client0Addr);
        Bytes callData = ABIEncoder.encodeBalanceOf(client0);
        Map<String, Object> callObj = Map.of(
            "to", Block.IST_COIN_ADDRESS,
            "data", callData.toHexString()
        );
        JsonObject resp = sendRpc("eth_call", List.of(callObj, "latest"));
        assertFalse(resp.has("error"), "eth_call should not return error");
        String result = resp.get("result").getAsString();
        assertTrue(result.startsWith("0x") && result.length() > 2, "Result should be non-empty hex");
        BigInteger balance = new BigInteger(result.substring(2), 16);
        assertTrue(balance.compareTo(BigInteger.ZERO) > 0, "client0 IST balance should be non-zero");
    }

    @Test
    public void testDepSendTransaction() throws Exception {
        Transaction tx = new Transaction(-1,
            Address.fromHexString(client0Addr),
            Address.fromHexString(Block.IST_COIN_ADDRESS),
            0L, new byte[0], 21000L, 1L, 0L, 0, null);
        String txJson = GsonUtils.GSON.toJson(tx);
        JsonObject resp = sendRpc("dep_sendTransaction", List.of(txJson));
        assertFalse(resp.has("error"), "dep_sendTransaction should not return error");
        assertTrue(resp.has("result"), "dep_sendTransaction should return a result");
    }

    @Test
    public void testUnknownMethod() throws Exception {
        JsonObject resp = sendRpc("dep_bogus", List.of());
        assertTrue(resp.has("error"), "Unknown method should return error");
        assertEquals(-32601, resp.get("error").getAsJsonObject().get("code").getAsInt());
    }

    @Test
    public void testMalformedJson() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("not json"))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject resp = JsonParser.parseString(response.body()).getAsJsonObject();
        assertTrue(resp.has("error"), "Malformed JSON should return error");
        assertEquals(-32700, resp.get("error").getAsJsonObject().get("code").getAsInt());
    }
}

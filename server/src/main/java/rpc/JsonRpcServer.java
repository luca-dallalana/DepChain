package rpc;

import blockchain.Account;
import blockchain.Block;
import blockchain.BlockchainMember;
import blockchain.Transaction;
import blockchain.evm.EVMHelper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import network.GsonUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class JsonRpcServer {

    private static final String CHAIN_ID = "0x539";

    private final int port;
    private final Supplier<Block> stateProvider;
    private final Consumer<Transaction> txSubmitter;
    private final Function<String, Block> blockLookup;
    private HttpServer httpServer;

    private static class RpcRequest {
        String jsonrpc;
        String method;
        List<Object> params;
        Object id;
    }

    private static class RpcResponse {
        String jsonrpc = "2.0";
        Object result;
        RpcError error;
        Object id;
    }

    private static class RpcError {
        int code;
        String message;

        RpcError(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    public JsonRpcServer(int port, Supplier<Block> stateProvider,
                         Consumer<Transaction> txSubmitter, Function<String, Block> blockLookup) {
        this.port = port;
        this.stateProvider = stateProvider;
        this.txSubmitter = txSubmitter;
        this.blockLookup = blockLookup;
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        httpServer.createContext("/", this::handle);
        httpServer.start();
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        String responseBody;
        try {
            byte[] requestBytes = exchange.getRequestBody().readAllBytes();
            String requestJson = new String(requestBytes);

            RpcRequest req;
            try {
                req = GsonUtils.GSON.fromJson(requestJson, RpcRequest.class);
                if (req == null || req.method == null) throw new IllegalStateException("missing method");
            } catch (Exception e) {
                responseBody = errorResponse(null, -32700, "Parse error");
                sendResponse(exchange, responseBody);
                return;
            }

            Object result;
            try {
                result = dispatch(req.method, req.params);
            } catch (UnsupportedOperationException e) {
                responseBody = errorResponse(req.id, -32601, "Method not found");
                sendResponse(exchange, responseBody);
                return;
            } catch (IllegalArgumentException e) {
                responseBody = errorResponse(req.id, -32602, e.getMessage());
                sendResponse(exchange, responseBody);
                return;
            } catch (Exception e) {
                responseBody = errorResponse(req.id, -32603, "Internal error: " + e.getMessage());
                sendResponse(exchange, responseBody);
                return;
            }

            RpcResponse resp = new RpcResponse();
            resp.id = req.id;
            resp.result = result;
            responseBody = GsonUtils.GSON.toJson(resp);
        } catch (Exception e) {
            responseBody = errorResponse(null, -32700, "Parse error");
        }
        sendResponse(exchange, responseBody);
    }

    private void sendResponse(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String errorResponse(Object id, int code, String message) {
        RpcResponse resp = new RpcResponse();
        resp.id = id;
        resp.error = new RpcError(code, message);
        return GsonUtils.GSON.toJson(resp);
    }

    private Object dispatch(String method, List<Object> params) {
        switch (method) {
            case "eth_blockNumber": return ethBlockNumber();
            case "eth_chainId": return CHAIN_ID;
            case "eth_getBalance": return ethGetBalance(params);
            case "eth_getTransactionCount": return ethGetTransactionCount(params);
            case "dep_getLatestBlock": return depGetLatestBlock();
            case "eth_getBlockByHash": return ethGetBlockByHash(params);
            case "eth_call": return ethCall(params);
            case "dep_sendTransaction": return depSendTransaction(params);
            default: throw new UnsupportedOperationException(method);
        }
    }

    private String ethBlockNumber() {
        return "0x" + Long.toHexString(stateProvider.get().blockNumber);
    }

    private String ethGetBalance(List<Object> params) {
        if (params == null || params.isEmpty()) throw new IllegalArgumentException("missing address");
        Address addr = Address.fromHexString((String) params.get(0));
        Account account = stateProvider.get().state.getAccount(addr);
        long balance = account != null ? account.balance : 0L;
        return "0x" + Long.toHexString(balance);
    }

    private String ethGetTransactionCount(List<Object> params) {
        if (params == null || params.isEmpty()) throw new IllegalArgumentException("missing address");
        Address addr = Address.fromHexString((String) params.get(0));
        Account account = stateProvider.get().state.getAccount(addr);
        long nonce = account != null ? account.nonce_count : 0L;
        return "0x" + Long.toHexString(nonce);
    }

    private Object depGetLatestBlock() {
        return blockToMap(stateProvider.get());
    }

    private Object ethGetBlockByHash(List<Object> params) {
        if (params == null || params.isEmpty()) throw new IllegalArgumentException("missing block hash");
        String hash = (String) params.get(0);
        Block block = blockLookup.apply(hash);
        return block != null ? blockToMap(block) : null;
    }

    private String ethCall(List<Object> params) {
        if (params == null || params.isEmpty()) throw new IllegalArgumentException("missing call object");

        @SuppressWarnings("unchecked")
        Map<String, Object> callObj = (Map<String, Object>) params.get(0);

        String toStr = (String) callObj.get("to");
        if (toStr == null) throw new IllegalArgumentException("missing to field");

        String dataStr = callObj.containsKey("data") ? (String) callObj.get("data") : "0x";
        String fromStr = callObj.containsKey("from") ? (String) callObj.get("from") : Block.ADMIN_ADDRESS;

        Address to = Address.fromHexString(toStr);
        Address from = Address.fromHexString(fromStr);
        Bytes data = Bytes.fromHexString(dataStr.isEmpty() ? "0x" : dataStr);

        EVMHelper evm = new EVMHelper();
        BlockchainMember.initializeEVM(evm, stateProvider.get().state);

        EVMHelper.ExecutionResult result = evm.executeCall(from, to, data);
        if (!result.isSuccess()) throw new RuntimeException("call reverted");

        return result.getReturnData().toHexString();
    }

    private String depSendTransaction(List<Object> params) {
        if (params == null || params.isEmpty()) throw new IllegalArgumentException("missing transaction json");
        String txJson = (String) params.get(0);
        Transaction tx = GsonUtils.GSON.fromJson(txJson, Transaction.class);
        if (tx == null) throw new IllegalArgumentException("invalid transaction json");
        txSubmitter.accept(tx);
        return "0x" + Long.toHexString(Math.abs((long) txJson.hashCode()));
    }

    private Map<String, Object> blockToMap(Block block) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("blockHash", block.blockHash != null ? block.blockHash : "0x0");
        map.put("parentBlockHash", block.parentBlockHash != null ? block.parentBlockHash : "0x0");
        map.put("blockNumber", "0x" + Long.toHexString(block.blockNumber));
        map.put("baseFeePerGas", "0x" + Long.toHexString(block.baseFeePerGas));
        map.put("totalGasUsed", "0x" + Long.toHexString(block.totalGasUsed));
        map.put("transactionCount", "0x" + Long.toHexString(block.transactions != null ? block.transactions.size() : 0));
        return map;
    }
}

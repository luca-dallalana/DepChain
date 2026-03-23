package blockchain.evm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.fluent.EVMExecutor;
import org.hyperledger.besu.evm.fluent.SimpleWorld;
import org.hyperledger.besu.evm.tracing.StandardJsonTracer;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigInteger;

public class EVMHelper {

    public final SimpleWorld world;
    private final ByteArrayOutputStream traceOutput;
    private final StandardJsonTracer tracer;

    public static class ExecutionResult {
        private final boolean success;
        private final Bytes returnData;
        private final long gasUsed;

        public ExecutionResult(boolean success, Bytes returnData, long gasUsed) {
            this.success = success;
            this.returnData = returnData;
            this.gasUsed = gasUsed;
        }

        public boolean isSuccess() { return success; }
        public Bytes getReturnData() { return returnData; }
        public long getGasUsed() { return gasUsed; }
    }

    public EVMHelper() {
        this.world = new SimpleWorld();
        this.traceOutput = new ByteArrayOutputStream();
        this.tracer = new StandardJsonTracer(
            new PrintStream(traceOutput),
            true, true, true, true  // showMemory, showStack, showStorage, showReturnData
        );
    }

    public void createAccount(Address address, Wei balance) {
        world.createAccount(address, 0, balance);
    }

    private EVMExecutor createExecutor() {
        var executor = EVMExecutor.evm(EvmSpecVersion.CANCUN);
        executor.tracer(tracer);
        executor.worldUpdater(world.updater());
        executor.commitWorldState();
        return executor;
    }

    public boolean deployContract(Address deployer, Address contractAddress, Bytes deploymentBytecode) {
        clearTrace();
        world.createAccount(contractAddress, 0, Wei.ZERO);

        var executor = createExecutor();
        executor.code(deploymentBytecode);
        executor.sender(deployer);
        executor.receiver(contractAddress);
        executor.execute();

        Bytes runtimeCode = extractReturnDataFromTrace();
        if (runtimeCode != null && !runtimeCode.isEmpty()) {
            var account = (org.hyperledger.besu.evm.account.MutableAccount) world.get(contractAddress);
            if (account != null) {
                account.setCode(runtimeCode);
                return true;
            }
        }
        return false;
    }

    public ExecutionResult executeCall(Address caller, Address contract, Bytes callData) {
        clearTrace();

        var account = world.get(contract);
        if (account == null) return new ExecutionResult(false, Bytes.EMPTY, 0);

        Bytes code = account.getCode();
        if (code == null || code.isEmpty()) return new ExecutionResult(false, Bytes.EMPTY, 0);

        var executor = createExecutor();
        executor.code(code);
        executor.sender(caller);
        executor.receiver(contract);
        executor.callData(callData);
        executor.execute();

        // Extract gas used
        long gasUsed = extractGasUsedFromTrace();

        // Check for success: must have RETURN and no REVERT REDUNDANT VERIFICATION
        if (hasOpcode("REVERT", "253", "INVALID", "254") || !hasOpcode("RETURN", "243")) {
            return new ExecutionResult(false, Bytes.EMPTY, gasUsed);
        }

        return new ExecutionResult(true, extractReturnDataFromTrace(), gasUsed);
    }

    public BigInteger extractUint256FromReturnData() {
        Bytes data = extractReturnDataFromTrace();
        if (data == null || data.isEmpty() || data.size() < 32) {
            return BigInteger.ZERO;
        }
        // Take first 32 bytes and convert to BigInteger
        String hexString = data.slice(0, Math.min(32, data.size())).toHexString();
        // Remove "0x" prefix if present
        if (hexString.startsWith("0x")) {
            hexString = hexString.substring(2);
        }
        return new BigInteger(hexString, 16);
    }

    public boolean extractBoolFromReturnData() {
        return !extractUint256FromReturnData().equals(BigInteger.ZERO);
    }

    // Helper: Check if trace contains any of the given opcodes
    private boolean hasOpcode(String... opcodes) {
        String[] lines = traceOutput.toString().split("\\r?\\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            try {
                JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                if (json.has("op")) {
                    String op = json.get("op").getAsString();
                    for (String opcode : opcodes) {
                        if (opcode.equals(op)) return true;
                    }
                }
            } catch (Exception e) { /* ignore malformed lines */ }
        }
        return false;
    }

    // Helper: Get last trace entry (before RETURN/REVERT)
    private JsonObject getLastTraceEntry() {
        try {
            String[] lines = traceOutput.toString().split("\\r?\\n");
            if (lines.length == 0) return null;
            return JsonParser.parseString(lines[lines.length - 1]).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private Bytes extractReturnDataFromTrace() {
        try {
            JsonObject json = getLastTraceEntry();
            if (json == null) return Bytes.EMPTY;

            String memory = json.get("memory").getAsString();
            JsonArray stack = json.get("stack").getAsJsonArray();

            if (stack.size() < 2) return Bytes.EMPTY;

            int offset = Integer.decode(stack.get(stack.size() - 1).getAsString());
            int size = Integer.decode(stack.get(stack.size() - 2).getAsString());

            return Bytes.fromHexString(memory.substring(2 + offset * 2, 2 + offset * 2 + size * 2));
        } catch (Exception e) {
            return Bytes.EMPTY;
        }
    }

    private long extractGasUsedFromTrace() {
        try {
            String[] lines = traceOutput.toString().split("\\r?\\n");
            if (lines.length == 0) return 0;

            JsonObject firstLine = JsonParser.parseString(lines[0]).getAsJsonObject();
            JsonObject lastLine = JsonParser.parseString(lines[lines.length - 1]).getAsJsonObject();

            long initialGas = Long.decode(firstLine.get("gas").getAsString());
            long finalGas = Long.decode(lastLine.get("gas").getAsString());

            return initialGas - finalGas;
        } catch (Exception e) {
            return 0;
        }
    }

    private void clearTrace() {
        traceOutput.reset();
    }

    // Public method to get trace output for debugging
    public String getTraceOutput() {
        return traceOutput.toString();
    }

    // Print last N lines of trace (useful for seeing REVERT/RETURN)
    public void printLastTraceLines(int numLines, String label) {
        System.out.println("\n  === TRACE: " + label + " ===");
        String[] lines = traceOutput.toString().split("\\r?\\n");
        int start = Math.max(0, lines.length - numLines);
        for (int i = start; i < lines.length; i++) {
            System.out.println("  " + lines[i]);
        }
        System.out.println("  === END TRACE ===\n");
    }
}

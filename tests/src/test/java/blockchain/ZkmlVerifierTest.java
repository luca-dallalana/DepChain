package blockchain;

import blockchain.evm.ABIEncoder;
import blockchain.evm.EVMHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ZkmlVerifierTest {

    private static final Address CALLER           = Address.fromHexString("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    private static final Address VERIFIER_ADDRESS = Address.fromHexString("0xcccccccccccccccccccccccccccccccccccccccc");

    private static String verifierBytecodeHex;
    private static byte[] proofBytes;
    private static List<BigInteger> instances;

    private EVMHelper evm;

    private static final String CARGO = System.getProperty("user.home") + "/.cargo/bin/cargo";
    private static final String SOLC  = "/opt/homebrew/bin/solc";

    @BeforeAll
    static void buildPipeline() throws Exception {
        Process build = new ProcessBuilder(CARGO, "build", "--release", "--manifest-path", "../zkml/Cargo.toml")
            .inheritIO()
            .start();
        assertEquals(0, build.waitFor(), "cargo build must succeed");

        Process run = new ProcessBuilder("../zkml/target/release/zkml", "../zkml")
            .inheritIO()
            .start();
        assertEquals(0, run.waitFor(), "zkml pipeline must succeed");

        Process compile = new ProcessBuilder(
            SOLC, "--optimize", "--bin", "--overwrite",
            "--evm-version", "prague",
            "-o", "../zkml/output/",
            "../zkml/output/verifier.sol"
        ).inheritIO().start();
        assertEquals(0, compile.waitFor(), "solc compile must succeed");

        verifierBytecodeHex = Files.readString(Paths.get("../zkml/output/Halo2Verifier.bin")).trim();

        String proofJson = Files.readString(Paths.get("../zkml/output/proof.json"));
        JsonObject json = JsonParser.parseString(proofJson).getAsJsonObject();

        String hexProof = json.get("hex_proof").getAsString();
        proofBytes = Bytes.fromHexString(hexProof).toArrayUnsafe();

        JsonArray outputs = json.getAsJsonObject("pretty_public_inputs")
            .getAsJsonArray("outputs")
            .get(0).getAsJsonArray();
        instances = new ArrayList<>();
        for (int i = 0; i < outputs.size(); i++) {
            String hexVal = outputs.get(i).getAsString();
            instances.add(new BigInteger(hexVal.substring(2), 16));
        }
    }

    @BeforeEach
    void setup() {
        evm = new EVMHelper();
        evm.createAccount(CALLER, Wei.fromEth(100));
        evm.deployContract(CALLER, VERIFIER_ADDRESS, Bytes.fromHexString(verifierBytecodeHex));
    }

    @Test
    void testRustBinaryExists() {
        assertTrue(Files.exists(Paths.get("../zkml/target/release/zkml")), "Rust binary must exist after build");
    }

    @Test
    void testVerifierSolGenerated() throws Exception {
        assertTrue(Files.size(Paths.get("../zkml/output/verifier.sol")) > 0, "verifier.sol must be non-empty");
    }

    @Test
    void testProofJsonGenerated() throws Exception {
        assertTrue(Files.size(Paths.get("../zkml/output/proof.json")) > 0, "proof.json must be non-empty");
    }

    @Test
    void testVerifierBytecodeNonEmpty() {
        assertNotNull(verifierBytecodeHex);
        assertTrue(verifierBytecodeHex.length() > 0, "verifier bytecode must be non-empty");
    }

    @Test
    void testVerifierDeploysSuccessfully() {
        EVMHelper fresh = new EVMHelper();
        fresh.createAccount(CALLER, Wei.fromEth(100));
        assertTrue(fresh.deployContract(CALLER, VERIFIER_ADDRESS, Bytes.fromHexString(verifierBytecodeHex)),
            "verifier deploy must succeed");
    }

    @Test
    void testVerifierIsContract() {
        var account = evm.world.get(VERIFIER_ADDRESS);
        assertNotNull(account, "verifier account must exist after deploy");
        assertTrue(account.getCode() != null && account.getCode().size() > 0,
            "verifier must have deployed code");
    }

    @Test
    void testProofBytesNonEmpty() {
        assertNotNull(proofBytes);
        assertTrue(proofBytes.length > 0, "proof bytes must be non-empty");
    }

    @Test
    void testInstancesNonEmpty() {
        assertNotNull(instances);
        assertTrue(instances.size() > 0, "instances list must be non-empty");
    }

    @Test
    void testVerifyProofReturnsTrue() {
        Bytes callData = ABIEncoder.encodeVerifyProof(proofBytes, instances);
        EVMHelper.ExecutionResult result = evm.executeCall(CALLER, VERIFIER_ADDRESS, callData);
        assertTrue(result.isSuccess(), "verifyProof call must not revert");
        assertTrue(evm.extractBoolFromReturnData(), "verifyProof must return true for valid proof");
    }

    @Test
    void testCorruptedProofReverts() {
        byte[] corrupted = proofBytes.clone();
        corrupted[0] ^= (byte) 0xFF;
        Bytes callData = ABIEncoder.encodeVerifyProof(corrupted, instances);
        EVMHelper.ExecutionResult result = evm.executeCall(CALLER, VERIFIER_ADDRESS, callData);
        assertFalse(result.isSuccess(), "verifyProof must revert with corrupted proof");
    }
}

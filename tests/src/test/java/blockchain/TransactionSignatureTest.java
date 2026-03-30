package blockchain;

import crypto.CryptoLib;
import network.GsonUtils;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionSignatureTest {

    private String projectRoot;
    private Address client0Address;
    private Address client1Address;
    private String client0PrivateKeyPath;
    private String client0PublicKeyPath;
    private String client1PrivateKeyPath;
    private String client1PublicKeyPath;

    @BeforeEach
    public void setup() {
        projectRoot = "..";

        // Generate addresses from public keys
        client0PublicKeyPath = projectRoot + "/rsa_keys/client_0/client_0.pubkey";
        client0PrivateKeyPath = projectRoot + "/rsa_keys/client_0/client_0.privatekey";
        client1PublicKeyPath = projectRoot + "/rsa_keys/client_1/client_1.pubkey";
        client1PrivateKeyPath = projectRoot + "/rsa_keys/client_1/client_1.privatekey";

        String client0Addr = AddressUtils.generateAddressFromPublicKey(client0PublicKeyPath);
        String client1Addr = AddressUtils.generateAddressFromPublicKey(client1PublicKeyPath);

        client0Address = Address.fromHexString(client0Addr);
        client1Address = Address.fromHexString(client1Addr);

        // Verify key files exist
        assertTrue(new File(client0PrivateKeyPath).exists(), "Client 0 private key must exist");
        assertTrue(new File(client0PublicKeyPath).exists(), "Client 0 public key must exist");
        assertTrue(new File(client1PrivateKeyPath).exists(), "Client 1 private key must exist");
        assertTrue(new File(client1PublicKeyPath).exists(), "Client 1 public key must exist");
    }

    @Test
    public void testValidSignatureAccepted() throws Exception {
        // Create transaction from Client0
        Transaction tx = new Transaction(
            4000,
            client0Address,
            client1Address,
            1000L,
            new byte[0],
            21000L,
            1L,
            0,
            null
        );

        String unsignedJson = GsonUtils.GSON.toJson(tx);
        byte[] signature = CryptoLib.sign(unsignedJson.getBytes(), client0PrivateKeyPath);
        tx.signature = signature;

        Transaction unsignedTx = new Transaction(tx.senderPort, tx.from, tx.to, tx.value,
                                                 tx.data, tx.gasLimit, tx.gasPrice,
                                                 tx.nonce_count, null);
        byte[] transactionBytes = GsonUtils.GSON.toJson(unsignedTx).getBytes();

        boolean isValid = CryptoLib.verifySignature(transactionBytes, signature, client0PublicKeyPath);

        assertTrue(isValid, "Valid signature from correct client should be accepted");
    }

    @Test
    public void testForgedSignatureRejection() throws Exception {
        Transaction tx = new Transaction(
            4000,
            client0Address,  // Claims to be from Client0
            client1Address,
            1000L,
            new byte[0],
            21000L,
            1L,
            0,
            null
        );

        // Malicious Client1 signs the transaction with their own key (FORGED)
        String unsignedJson = GsonUtils.GSON.toJson(tx);
        byte[] forgedSignature = CryptoLib.sign(unsignedJson.getBytes(), client1PrivateKeyPath);
        tx.signature = forgedSignature;

        // Server verifies signature using Client0's public key (extracted from sender port)
        Transaction unsignedTx = new Transaction(tx.senderPort, tx.from, tx.to, tx.value,
                                                 tx.data, tx.gasLimit, tx.gasPrice,
                                                 tx.nonce_count, null);
        byte[] transactionBytes = GsonUtils.GSON.toJson(unsignedTx).getBytes();

        boolean isValid = CryptoLib.verifySignature(transactionBytes, forgedSignature, client0PublicKeyPath);

        assertFalse(isValid, "Forged signature should be REJECTED");
    }

    @Test
    public void testWrongSenderAddressWithValidSignature() throws Exception {
        // Malicious Client1 creates transaction claiming to be from Client0
        Transaction tx = new Transaction(
            4001,  // Client1's port (Byzantine attacker)
            client0Address,  // Claims to be from Client0 (victim)
            client1Address,  // Sending to themselves
            1000L,
            new byte[0],
            21000L,
            1L,
            0,
            null
        );

        // Client1 signs with their own key
        String unsignedJson = GsonUtils.GSON.toJson(tx);
        byte[] signature = CryptoLib.sign(unsignedJson.getBytes(), client1PrivateKeyPath);
        tx.signature = signature;

        // Server extracts client ID from senderPort (4001 - 4000 = 1)
        int senderId = tx.senderPort - 4000;
        String publicKeyPath = projectRoot + "/rsa_keys/client_" + senderId + "/client_" + senderId + ".pubkey";

        // Server verifies signature using Client1's public key (from port)
        Transaction unsignedTx = new Transaction(tx.senderPort, tx.from, tx.to, tx.value,
                                                 tx.data, tx.gasLimit, tx.gasPrice,
                                                 tx.nonce_count, null);
        byte[] transactionBytes = GsonUtils.GSON.toJson(unsignedTx).getBytes();

        boolean isValid = CryptoLib.verifySignature(transactionBytes, signature, publicKeyPath);

        assertTrue(isValid, "Signature is technically valid (signed by Client1)");

        // However, the FROM address doesn't match the signer!
        String signerAddress = AddressUtils.generateAddressFromPublicKey(publicKeyPath);
        Address actualSignerAddress = Address.fromHexString(signerAddress);

        boolean addressesMatch = tx.from.equals(actualSignerAddress);
        assertFalse(addressesMatch, "FROM address should NOT match signer's address");
    }
}

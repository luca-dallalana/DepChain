package crypto;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.spec.PKCS8EncodedKeySpec;

public class CryptoLib {

    /* ===== Diffie–Hellman ===== */

    public static KeyPair generateDHKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
        kpg.initialize(2048);
        
        return kpg.generateKeyPair();
    }
    
    public static KeyPair generateDHKeyPairReceiver(PublicKey receiverPublicKey) throws Exception {
        DHParameterSpec params = ((DHPublicKey) receiverPublicKey).getParams();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("DiffieHellman");
        kpg.initialize(params);
        
        return kpg.generateKeyPair();
    }

    public static byte[] computeSharedSecret(PrivateKey dhPrivateKey, PublicKey peerDhPublicKey) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("DH");
        ka.init(dhPrivateKey);
        ka.doPhase(peerDhPublicKey, true);
        
        return ka.generateSecret();
    }

    public static SecretKey deriveHmacKey(byte[] sharedSecret) {
        return new SecretKeySpec(sharedSecret, 0, 32, "HmacSHA256");
    }

     /* ===== RSA ===== */

    private static PublicKey readPublicKey(String publicKeyPath)
            throws GeneralSecurityException, IOException {

        FileInputStream pubFis = new FileInputStream(publicKeyPath);
        byte[] pubEncoded = new byte[pubFis.available()];
        pubFis.read(pubEncoded);
        pubFis.close();

        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubEncoded);
        KeyFactory keyFacPub = KeyFactory.getInstance("RSA");
        PublicKey pub = keyFacPub.generatePublic(pubSpec);

        return pub;
    }

    private static PrivateKey readPrivateKey(String privateKeyPath)
            throws GeneralSecurityException, IOException {

        FileInputStream privFis = new FileInputStream(privateKeyPath);
        byte[] privEncoded = new byte[privFis.available()];
        privFis.read(privEncoded);
        privFis.close();

        PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privEncoded);
        KeyFactory keyFacPriv = KeyFactory.getInstance("RSA");
        PrivateKey priv = keyFacPriv.generatePrivate(privSpec);

        return priv;
    }

    // Used only to authenticate DH public keys
    public static byte[] sign(byte[] data, String privateKeyPath) throws Exception {
        PrivateKey privKey = readPrivateKey(privateKeyPath);
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privKey);
        sig.update(data);
        return sig.sign();
    }

    // Used only to verify authenticity of DH public keys
    public static boolean verifySignature(byte[] data, byte[] signature, String publicKeyPath) throws Exception {
        PublicKey pubKey = readPublicKey(publicKeyPath);
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(pubKey);
        sig.update(data);
        boolean verified = sig.verify(signature);
        return verified;
    }
    
    /* ===== HMAC ===== */

    public static String computeHmac(byte[] data, SecretKey key) throws Exception{
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(key);
        byte[] hmac = mac.doFinal(data);
        return Base64.getEncoder().encodeToString(hmac);
    }

    public static boolean verifyHmac(byte[] data, byte[] receivedHmac, SecretKey key) throws Exception {
        String hmac = computeHmac(data, key);
        String receivedHmacStr = Base64.getEncoder().encodeToString(receivedHmac);
        return hmac.equals(receivedHmacStr);
    }

    /* ===== Key decoding ===== */

    public static PublicKey decodeDHPublicKey(byte[] encodedKey) throws Exception {
            return KeyFactory.getInstance("DH").generatePublic(new X509EncodedKeySpec(encodedKey));

    }

    /* ===== Generic Hashing ===== */

    public static byte[] hash(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(data);
    }

    public static String hashToString(byte[] hash) {
        return Base64.getEncoder().encodeToString(hash);
    }
}

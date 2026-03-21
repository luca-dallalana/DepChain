package blockchain;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;

public class AddressUtils {

    public static String generateAddressFromPublicKey(String publicKeyPath) {
        try {
            byte[] publicKeyBytes = Files.readAllBytes(Paths.get(publicKeyPath));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(publicKeyBytes);

            StringBuilder address = new StringBuilder("0x");
            for (int i = hash.length - 20; i < hash.length; i++) {
                address.append(String.format("%02x", hash[i] & 0xff));
            }

            return address.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate address from public key: " + publicKeyPath, e);
        }
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b & 0xff));
        }
        return result.toString();
    }
}

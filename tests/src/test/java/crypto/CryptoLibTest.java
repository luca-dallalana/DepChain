package crypto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import javax.crypto.SecretKey;

public class CryptoLibTest {
    @Test
    public void testHmacIntegrityFail() throws Exception {
        String message = "Integrity test!";
        String tamperedMessage = "Integrity test?";
        byte[] messageBytes = message.getBytes();
        byte[] tamperedBytes = tamperedMessage.getBytes();
        
        byte[] sharedSecret = new byte[32];
        for (int i = 0; i < sharedSecret.length; i++) sharedSecret[i] = (byte) i;
        SecretKey key = CryptoLib.deriveHmacKey(sharedSecret);

        String hmac = CryptoLib.computeHmac(messageBytes, key);
        byte[] hmacBytes = java.util.Base64.getDecoder().decode(hmac);

        boolean valid = CryptoLib.verifyHmac(tamperedBytes, hmacBytes, key);
        if(!valid){
            System.out.println("-----------------------------------------");
            System.out.println("HMAC is invalid, integrity compromised!!!");
            System.out.println("-----------------------------------------");
        }
        assertFalse(valid, "Tampered message should not pass HMAC verification");
    }
}

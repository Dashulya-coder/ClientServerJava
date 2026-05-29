package protocol;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class CryptoService {

    private final SecretKeySpec key;

    public CryptoService(byte[] keyBytes) {
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public byte[] encrypt(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(data);
    }

    public byte[] decrypt(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(data);
    }
}
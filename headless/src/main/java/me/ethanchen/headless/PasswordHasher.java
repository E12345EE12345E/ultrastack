package me.ethanchen.headless;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

public class PasswordHasher {
    private static final int ITERATIONS = 100_000;
    private static final int KEY_LENGTH = 256; // bits
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    public static byte[] generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    public static byte[] hash(String passcode, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(passcode.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }

    public static boolean verify(String passcode, byte[] salt, byte[] expectedHash) {
        byte[] actual = hash(passcode, salt);
        return Arrays.equals(actual, expectedHash);
    }
}

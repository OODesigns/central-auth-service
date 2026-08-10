package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.KeyPassword;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Shared helper for encrypting and decrypting TOTP secrets stored in the database.
 * <p>
 * Uses AES/CBC/PKCS5Padding with a derived 256-bit key and a random IV prefixed to the
 * ciphertext. The IV format is internal to this adapter package so both setup and verifier
 * can use the same storage format.
 */
final class TotpSecretCipher {

    private static final String CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16;
    private static final String KEY_ALGORITHM = "AES";

    private TotpSecretCipher() {
    }

    static byte[] encrypt(final String plaintext, final KeyPassword password, final SecureRandom random) {
        Objects.requireNonNull(plaintext, "Plaintext cannot be null");
        Objects.requireNonNull(password, "Key password cannot be null");
        Objects.requireNonNull(random, "SecureRandom cannot be null");

        final byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);

        final byte[] encrypted = transform(Cipher.ENCRYPT_MODE, plaintext.getBytes(StandardCharsets.UTF_8), password, iv);
        try {
            return ByteBuffer.allocate(iv.length + encrypted.length)
                .put(iv)
                .put(encrypted)
                .array();
        } finally {
            Arrays.fill(encrypted, (byte) 0);
            Arrays.fill(iv, (byte) 0);
        }
    }

    static String decrypt(final byte[] ciphertext, final KeyPassword password) {
        Objects.requireNonNull(ciphertext, "Ciphertext cannot be null");
        Objects.requireNonNull(password, "Key password cannot be null");
        if (ciphertext.length <= IV_LENGTH) {
            throw new IllegalArgumentException("Ciphertext is too short");
        }

        final byte[] iv = Arrays.copyOfRange(ciphertext, 0, IV_LENGTH);
        final byte[] payload = Arrays.copyOfRange(ciphertext, IV_LENGTH, ciphertext.length);
        try {
            final byte[] decrypted = transform(Cipher.DECRYPT_MODE, payload, password, iv);
            try {
                return new String(decrypted, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(decrypted, (byte) 0);
            }
        } finally {
            Arrays.fill(iv, (byte) 0);
            Arrays.fill(payload, (byte) 0);
        }
    }

    private static byte[] transform(final int mode,
                                    final byte[] input,
                                    final KeyPassword password,
                                    final byte[] iv) {
        final byte[] keyBytes = deriveKey(password);
        try {
            final Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(mode, new SecretKeySpec(keyBytes, KEY_ALGORITHM), new IvParameterSpec(iv));
            return cipher.doFinal(input);
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to process TOTP secret", e);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    private static byte[] deriveKey(final KeyPassword password) {
        final byte[] raw = password.toUtf8Bytes();
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(raw);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required for TOTP secret encryption", e);
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }
}

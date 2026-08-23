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
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Shared helper for encrypting and decrypting TOTP secrets stored in the database.
 * <p>
 * New writes use a versioned AES-GCM envelope. Untagged legacy AES-CBC values remain
 * readable during migration.
 */
final class TotpSecretCipher {

    private static final byte[] MAGIC = {'C', 'A', 'S'};
    private static final byte VERSION_GCM = 2;
    private static final byte[] GCM_HEADER = {'C', 'A', 'S', VERSION_GCM};
    private static final String GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String LEGACY_TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int GCM_NONCE_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int LEGACY_IV_LENGTH = 16;
    private static final String KEY_ALGORITHM = "AES";

    private TotpSecretCipher() {
    }

    static byte[] encrypt(final String plaintext, final KeyPassword password, final SecureRandom random) {
        Objects.requireNonNull(plaintext, "Plaintext cannot be null");
        Objects.requireNonNull(password, "Key password cannot be null");
        Objects.requireNonNull(random, "SecureRandom cannot be null");

        final byte[] nonce = new byte[GCM_NONCE_LENGTH];
        random.nextBytes(nonce);

        final byte[] encrypted = transformGcm(
            Cipher.ENCRYPT_MODE, plaintext.getBytes(StandardCharsets.UTF_8), password, nonce);
        try {
            return ByteBuffer.allocate(GCM_HEADER.length + nonce.length + encrypted.length)
                .put(GCM_HEADER)
                .put(nonce)
                .put(encrypted)
                .array();
        } finally {
            Arrays.fill(encrypted, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
        }
    }

    static String decrypt(final byte[] ciphertext, final KeyPassword password) {
        Objects.requireNonNull(ciphertext, "Ciphertext cannot be null");
        Objects.requireNonNull(password, "Key password cannot be null");
        if (hasMagic(ciphertext)) {
            if (!hasGcmHeader(ciphertext)) {
                throw new IllegalArgumentException("Unsupported TOTP ciphertext version");
            }
            return decryptGcm(ciphertext, password);
        }
        return decryptLegacy(ciphertext, password);
    }

    private static String decryptGcm(final byte[] ciphertext, final KeyPassword password) {
        if (ciphertext.length <= GCM_HEADER.length + GCM_NONCE_LENGTH + GCM_TAG_BITS / Byte.SIZE) {
            throw new IllegalArgumentException("Ciphertext is too short");
        }

        final byte[] nonce = Arrays.copyOfRange(ciphertext, GCM_HEADER.length, GCM_HEADER.length + GCM_NONCE_LENGTH);
        final byte[] payload = Arrays.copyOfRange(ciphertext, GCM_HEADER.length + GCM_NONCE_LENGTH, ciphertext.length);
        try {
            final byte[] decrypted = transformGcm(Cipher.DECRYPT_MODE, payload, password, nonce);
            try {
                return new String(decrypted, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(decrypted, (byte) 0);
            }
        } finally {
            Arrays.fill(nonce, (byte) 0);
            Arrays.fill(payload, (byte) 0);
        }
    }

    private static String decryptLegacy(final byte[] ciphertext, final KeyPassword password) {
        if (ciphertext.length <= LEGACY_IV_LENGTH) {
            throw new IllegalArgumentException("Ciphertext is too short");
        }
        final byte[] iv = Arrays.copyOfRange(ciphertext, 0, LEGACY_IV_LENGTH);
        final byte[] payload = Arrays.copyOfRange(ciphertext, LEGACY_IV_LENGTH, ciphertext.length);
        try {
            final byte[] decrypted = transformLegacy(payload, password, iv);
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

    private static byte[] transformGcm(final int mode,
                                       final byte[] input,
                                       final KeyPassword password,
                                       final byte[] nonce) {
        final byte[] keyBytes = deriveKey(password);
        try {
            final Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
            cipher.init(mode, new SecretKeySpec(keyBytes, KEY_ALGORITHM),
                new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(GCM_HEADER);
            return cipher.doFinal(input);
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to process TOTP secret", e);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    private static byte[] transformLegacy(final byte[] input,
                                          final KeyPassword password,
                                          final byte[] iv) {
        final byte[] keyBytes = deriveKey(password);
        try {
            final Cipher cipher = Cipher.getInstance(LEGACY_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, KEY_ALGORITHM),
                new IvParameterSpec(iv));
            return cipher.doFinal(input);
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to process legacy TOTP secret", e);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    private static boolean hasMagic(final byte[] ciphertext) {
        return ciphertext.length >= MAGIC.length
            && ciphertext[0] == MAGIC[0]
            && ciphertext[1] == MAGIC[1]
            && ciphertext[2] == MAGIC[2];
    }

    private static boolean hasGcmHeader(final byte[] ciphertext) {
        return ciphertext.length >= GCM_HEADER.length && ciphertext[3] == VERSION_GCM;
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

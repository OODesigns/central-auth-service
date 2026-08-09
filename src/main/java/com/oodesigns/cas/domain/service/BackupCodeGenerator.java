package com.oodesigns.cas.domain.service;

import com.oodesigns.cas.domain.value.BackupCode;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Domain service generating single-use backup (recovery) codes for 2FA.
 * <p>
 * JDK-only: uses {@link SecureRandom} — no third-party dependency, satisfying the
 * hexagonal rule that {@code domain} imports nothing outside the JDK.
 * <p>
 * Format: {@code XXXX-XXXX-XXXX-XXXX} as required by {@link BackupCode}.
 * <p>
 * Alphabet: 32 unambiguous symbols — {@code A-Z} excluding {@code I} and {@code O},
 * plus {@code 2-9}. Omitting {@code I/1/L} and {@code O/0} avoids transcription errors
 * when users copy codes off paper. 32 symbols is a power of two, so each symbol carries
 * exactly 5 bits and drawing one is free of modulo bias: 16 symbols = <b>80 bits</b> of
 * entropy per code.
 * <p>
 * SECURITY:
 * <ul>
 *   <li>Codes are returned in plaintext <em>once</em>, for immediate display to the user.
 *       Persisting them is the adapter's job and they must be BCrypt-hashed (via
 *       {@link Ports.TotpSetupProvider}) before storage — never stored in the clear.</li>
 *   <li>Every code is drawn from {@link SecureRandom}; no code is derived from another,
 *       so compromising one reveals nothing about the rest of the batch.</li>
 * </ul>
 */
public final class BackupCodeGenerator {

    /**
     * 32 unambiguous symbols (no I, O, 0, 1). Power of two → 5 bits per symbol, no modulo bias.
     */
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    /** Number of symbol groups in a code. */
    private static final int GROUPS = 4;

    /** Symbols per group. */
    private static final int GROUP_SIZE = 4;

    /** Separator between groups. */
    private static final char SEPARATOR = '-';

    /** Default number of codes issued when 2FA is enabled. */
    public static final int DEFAULT_BATCH_SIZE = 10;

    /** Guard rails for batch generation. */
    private static final int MIN_BATCH_SIZE = 1;
    private static final int MAX_BATCH_SIZE = 16;

    private final SecureRandom random;

    /**
     * Production constructor using the platform's default strong {@link SecureRandom}.
     */
    public BackupCodeGenerator() {
        this(new SecureRandom());
    }

    /**
     * Constructor allowing an explicit {@link SecureRandom}, so tests can seed a
     * deterministic instance and assert reproducible output.
     *
     * @param random the randomness source (must not be null)
     */
    public BackupCodeGenerator(final SecureRandom random) {
        this.random = Objects.requireNonNull(random, "SecureRandom is required");
    }

    /**
     * Generate a single backup code.
     *
     * @return a {@link BackupCode} in format XXXX-XXXX-XXXX-XXXX
     */
    public BackupCode generate() {
        final StringBuilder code = new StringBuilder(GROUPS * GROUP_SIZE + GROUPS - 1);
        for (int group = 0; group < GROUPS; group++) {
            if (group > 0) {
                code.append(SEPARATOR);
            }
            for (int symbol = 0; symbol < GROUP_SIZE; symbol++) {
                code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
            }
        }
        return BackupCode.of(code.toString());
    }

    /**
     * Generate the default batch of {@value #DEFAULT_BATCH_SIZE} backup codes.
     *
     * @return an unmodifiable list of distinct backup codes
     */
    public List<BackupCode> generateBatch() {
        return generateBatch(DEFAULT_BATCH_SIZE);
    }

    /**
     * Generate a batch of backup codes.
     * <p>
     * Duplicates are re-drawn, so the returned list always contains {@code count} distinct
     * codes. With 80 bits of entropy a collision is astronomically unlikely, but de-duplicating
     * guarantees that consuming one code can never invalidate another.
     *
     * @param count how many codes to generate, between {@value #MIN_BATCH_SIZE} and
     *              {@value #MAX_BATCH_SIZE}
     * @return an unmodifiable list of distinct backup codes
     * @throws IllegalArgumentException if count is outside the permitted range
     */
    public List<BackupCode> generateBatch(final int count) {
        if (count < MIN_BATCH_SIZE || count > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                String.format("Backup code batch size must be between %d and %d (requested %d)",
                    MIN_BATCH_SIZE, MAX_BATCH_SIZE, count));
        }

        final List<BackupCode> codes = new ArrayList<>(count);
        while (codes.size() < count) {
            final BackupCode candidate = generate();
            if (!codes.contains(candidate)) {
                codes.add(candidate);
            }
        }
        return List.copyOf(codes);
    }
}


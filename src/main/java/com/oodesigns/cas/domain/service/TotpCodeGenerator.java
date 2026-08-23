package com.oodesigns.cas.domain.service;

import com.oodesigns.cas.domain.value.SecretFor2FA;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Domain service implementing RFC 6238 (TOTP) on top of RFC 4226 (HOTP).
 * <p>
 * JDK-only: uses {@code javax.crypto.Mac} with HMAC-SHA1 — no third-party dependency,
 * satisfying the hexagonal rule that {@code domain} imports nothing outside the JDK.
 * <p>
 * Parameters (interoperable with Google Authenticator, Authy, 1Password, etc.):
 * <ul>
 *   <li>Algorithm: HMAC-SHA1</li>
 *   <li>Time step (X): 30 seconds</li>
 *   <li>Epoch (T0): 0 (Unix epoch)</li>
 *   <li>Digits: 6</li>
 *   <li>Validation skew: ±1 time step (accepts the previous and next window to tolerate
 *       clock drift and user typing latency)</li>
 * </ul>
 * <p>
 * SECURITY:
 * <ul>
 *   <li>{@link #verify} compares in constant time and evaluates <em>all</em> accepted
 *       windows without short-circuiting, so neither the code value nor which window
 *       matched can be inferred from response timing.</li>
 *   <li>The decoded secret key material is zeroed immediately after each HMAC.</li>
 *   <li>{@link #findMatchingCounter} exposes the matched RFC 6238 counter so an adapter can
 *       atomically reject replay without moving persistence into the domain.</li>
 * </ul>
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6238">RFC 6238</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc4226">RFC 4226</a>
 */
public final class TotpCodeGenerator {

    /** HMAC algorithm mandated by RFC 6238's default configuration. */
    static final String HMAC_SHA1 = "HmacSHA1";

    /** Number of digits in a generated code (RFC 4226 recommends 6..8). */
    private static final int DIGITS = 6;

    /** 10^DIGITS, used to truncate the dynamic binary code. */
    private static final int MODULUS = 1_000_000;

    /** Time step X from RFC 6238 section 4.1. */
    private static final Duration TIME_STEP = Duration.ofSeconds(30);

    /** Number of steps either side of "now" that {@link #verify} will accept. */
    private static final int SKEW_STEPS = 1;

    /** Base32 (RFC 4648) alphabet offset for the digits 2-7, which follow A-Z. */
    private static final int BASE32_DIGIT_OFFSET = 26;

    private final Ports.Clock clock;

    public TotpCodeGenerator(final Ports.Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    /**
     * Generate the TOTP code for the current time step.
     * <p>
     * Used when provisioning/enrolling, and by tests. Normal verification should use
     * {@link #verify}, which additionally tolerates clock skew.
     *
     * @param secret the user's Base32 TOTP secret
     * @return a zero-padded 6-digit code
     */
    public String generate(final SecretFor2FA secret) {
        Objects.requireNonNull(secret, "TOTP secret is required");
        return generateForTimeStep(secret, currentTimeStep());
    }

    /**
     * Verify a user-supplied code against the current window and ±1 step.
     * <p>
     * Every candidate window is evaluated and compared in constant time; the loop
     * deliberately uses a non-short-circuiting {@code |=} so that the total work is
     * independent of whether (and where) a match occurs.
     *
     * @param secret        the user's Base32 TOTP secret
     * @param candidateCode the code entered by the user (null/short/long are simply invalid)
     * @return true if the code is valid within the accepted window
     */
    public boolean verify(final SecretFor2FA secret, final String candidateCode) {
        return findMatchingCounter(secret, candidateCode).isPresent();
    }

    /**
     * Return the newest accepted RFC 6238 counter matching a candidate code.
     * Every accepted window is evaluated even after a match.
     */
    public OptionalLong findMatchingCounter(final SecretFor2FA secret, final String candidateCode) {
        Objects.requireNonNull(secret, "TOTP secret is required");
        if (candidateCode == null) {
            return OptionalLong.empty();
        }

        final long currentStep = currentTimeStep();
        long matchedCounter = -1;
        for (int offset = -SKEW_STEPS; offset <= SKEW_STEPS; offset++) {
            final long candidateCounter = currentStep + offset;
            if (constantTimeEquals(generateForTimeStep(secret, candidateCounter), candidateCode)) {
                matchedCounter = candidateCounter;
            }
        }
        return matchedCounter < 0 ? OptionalLong.empty() : OptionalLong.of(matchedCounter);
    }

    /**
     * The current RFC 6238 time step counter: T = floor((unixTime - T0) / X) with T0 = 0.
     */
    private long currentTimeStep() {
        return clock.now().getEpochSecond() / TIME_STEP.toSeconds();
    }

    /**
     * Compute the HOTP value (RFC 4226 section 5.3) for an explicit counter value.
     * The decoded key is wiped before returning.
     */
    private String generateForTimeStep(final SecretFor2FA secret, final long timeStep) {
        final byte[] key = decodeBase32(secret.getSecret());
        try {
            final byte[] counter = ByteBuffer.allocate(Long.BYTES).putLong(timeStep).array();
            return truncate(hmac(HMAC_SHA1, key, counter));
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /**
     * RFC 4226 section 5.3 dynamic truncation.
     * <p>
     * Takes the low nibble of the last byte as an offset, reads a big-endian 31-bit
     * integer from that offset (masking the sign bit), then reduces modulo 10^DIGITS.
     */
    private static String truncate(final byte[] hash) {
        final int offset = hash[hash.length - 1] & 0x0F;
        final int binary = ((hash[offset] & 0x7F) << 24)
                         | ((hash[offset + 1] & 0xFF) << 16)
                         | ((hash[offset + 2] & 0xFF) << 8)
                         | (hash[offset + 3] & 0xFF);
        return String.format("%0" + DIGITS + "d", binary % MODULUS);
    }

    /**
     * Compute an HMAC.
     * <p>
     * Package-private and algorithm-parameterised so the failure path is directly testable
     * and so SHA-256/SHA-512 TOTP variants can be added without restructuring.
     *
     * @throws IllegalStateException if the algorithm or key is rejected by the JCE provider
     */
    static byte[] hmac(final String algorithm, final byte[] key, final byte[] data) {
        try {
            final Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(data);
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("TOTP requires " + algorithm + ", which this JVM rejected", e);
        }
    }

    /**
     * Decode an RFC 4648 Base32 string to raw key bytes.
     * <p>
     * No character validation is performed here: {@link SecretFor2FA} already guarantees the
     * value matches {@code ^[A-Z2-7]+=*$}, so every character is a legal Base32 symbol.
     * Trailing padding contributes no bits and is skipped.
     */
    private static byte[] decodeBase32(final String base32) {
        final String unpadded = base32.replace("=", "");
        final byte[] decoded = new byte[unpadded.length() * 5 / 8];

        int buffer = 0;
        int bitsBuffered = 0;
        int index = 0;
        for (int i = 0; i < unpadded.length(); i++) {
            buffer = (buffer << 5) | base32Value(unpadded.charAt(i));
            bitsBuffered += 5;
            if (bitsBuffered >= 8) {
                bitsBuffered -= 8;
                decoded[index++] = (byte) (buffer >> bitsBuffered);
            }
        }
        // Any remaining <8 bits are Base32 padding bits and are intentionally discarded.
        return decoded;
    }

    /**
     * Map a Base32 symbol to its 5-bit value: 'A'-'Z' → 0..25, '2'-'7' → 26..31.
     */
    private static int base32Value(final char symbol) {
        return symbol >= 'A' ? symbol - 'A' : symbol - '2' + BASE32_DIGIT_OFFSET;
    }

    /**
     * Length-checked, constant-time string comparison.
     * <p>
     * Once the lengths match, every character is compared regardless of earlier mismatches,
     * so the running time does not depend on the position of the first differing character.
     */
    private static boolean constantTimeEquals(final String expected, final String actual) {
        if (expected.length() != actual.length()) {
            return false;
        }
        int difference = 0;
        for (int i = 0; i < expected.length(); i++) {
            difference |= expected.charAt(i) ^ actual.charAt(i);
        }
        return difference == 0;
    }
}


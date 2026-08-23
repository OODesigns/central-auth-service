package com.oodesigns.cas.domain.service;

import com.oodesigns.cas.domain.value.SecretFor2FA;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TotpCodeGenerator}.
 * <p>
 * Correctness is asserted against the official RFC 6238 Appendix B test vectors, so this
 * suite verifies interoperability with real authenticator apps rather than merely
 * self-consistency.
 */
class TotpCodeGeneratorTest {

    /**
     * Base32 of the RFC 6238 Appendix B seed "12345678901234567890" (20 ASCII bytes).
     */
    private static final String RFC6238_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private static final long TIME_STEP_SECONDS = 30L;

    private static TotpCodeGenerator generatorAt(final long epochSecond) {
        final Ports.Clock clock = () -> Instant.ofEpochSecond(epochSecond);
        return new TotpCodeGenerator(clock);
    }

    private static SecretFor2FA rfcSecret() {
        return SecretFor2FA.of(RFC6238_SECRET);
    }

    // ---------------------------------------------------------------- RFC 6238 vectors

    /**
     * RFC 6238 Appendix B (SHA-1 rows). The RFC publishes 8-digit codes; a 6-digit code is
     * the 8-digit value mod 10^6, because truncation is {@code binary % 10^digits}.
     */
    @ParameterizedTest(name = "RFC 6238 vector: t={0} -> {1}")
    @CsvSource({
        "59,           287082",
        "1111111109,   081804",
        "1111111111,   050471",
        "1234567890,   005924",
        "2000000000,   279037",
        "20000000000,  353130"
    })
    void generateMatchesRfc6238AppendixBVectors(final long epochSecond, final String expectedCode) {
        assertEquals(expectedCode, generatorAt(epochSecond).generate(rfcSecret()));
    }

    @Test
    void generateAlwaysReturnsSixDigits() {
        // 005924 exercises the zero-padding path — a naive implementation would emit "5924"
        assertEquals("005924", generatorAt(1234567890L).generate(rfcSecret()));
    }

    @Test
    void generateDecodesBase32SecretsWithPadding() {
        // Base32 of the 11-byte seed "12345678901"; 18 symbols + 6 '=' padding characters
        final SecretFor2FA paddedSecret = SecretFor2FA.of("GEZDGNBVGY3TQOJQGE======");
        assertEquals("543561", generatorAt(59L).generate(paddedSecret));
    }

    // ---------------------------------------------------------------- verification window

    @Test
    void verifyAcceptsCodeForCurrentTimeStep() {
        final long now = 1234567890L;
        assertTrue(generatorAt(now).verify(rfcSecret(), "005924"));
        assertEquals(now / TIME_STEP_SECONDS,
            generatorAt(now).findMatchingCounter(rfcSecret(), "005924").orElseThrow());
    }

    @Test
    void verifyAcceptsCodeFromPreviousTimeStep() {
        final long now = 1234567890L;
        final String previousCode = generatorAt(now - TIME_STEP_SECONDS).generate(rfcSecret());
        assertTrue(generatorAt(now).verify(rfcSecret(), previousCode),
            "A code from one step ago must be accepted (user typing latency)");
    }

    @Test
    void verifyAcceptsCodeFromNextTimeStep() {
        final long now = 1234567890L;
        final String nextCode = generatorAt(now + TIME_STEP_SECONDS).generate(rfcSecret());
        assertTrue(generatorAt(now).verify(rfcSecret(), nextCode),
            "A code from one step ahead must be accepted (client clock drift)");
    }

    @Test
    void verifyRejectsCodeTwoStepsInThePast() {
        final long now = 1234567890L;
        final String staleCode = generatorAt(now - 2 * TIME_STEP_SECONDS).generate(rfcSecret());
        assertFalse(generatorAt(now).verify(rfcSecret(), staleCode),
            "Skew must be bounded at +/-1 step; a 2-step-old code must expire");
    }

    @Test
    void verifyRejectsCodeTwoStepsInTheFuture() {
        final long now = 1234567890L;
        final String futureCode = generatorAt(now + 2 * TIME_STEP_SECONDS).generate(rfcSecret());
        assertFalse(generatorAt(now).verify(rfcSecret(), futureCode),
            "Skew must be bounded at +/-1 step; a 2-step-ahead code must be rejected");
    }

    // ---------------------------------------------------------------- rejection paths

    @Test
    void verifyRejectsNullCode() {
        assertFalse(generatorAt(1234567890L).verify(rfcSecret(), null));
        assertTrue(generatorAt(1234567890L).findMatchingCounter(rfcSecret(), null).isEmpty());
    }

    @Test
    void verifyRejectsWrongCode() {
        assertFalse(generatorAt(1234567890L).verify(rfcSecret(), "000000"));
    }

    @Test
    void verifyRejectsCodeOfWrongLength() {
        // Exercises the length guard in the constant-time comparison
        assertFalse(generatorAt(1234567890L).verify(rfcSecret(), "5924"));
        assertFalse(generatorAt(1234567890L).verify(rfcSecret(), "00592400"));
        assertFalse(generatorAt(1234567890L).verify(rfcSecret(), ""));
    }

    @Test
    void differentSecretsProduceDifferentCodes() {
        final SecretFor2FA otherSecret = SecretFor2FA.of("MFRGGZDFMZTWQ2LKMFRGGZDFMZTWQ2LK");
        final TotpCodeGenerator generator = generatorAt(1234567890L);
        assertNotEquals(generator.generate(rfcSecret()), generator.generate(otherSecret));
        assertFalse(generator.verify(otherSecret, "005924"),
            "A code minted from another secret must never verify");
    }

    // ---------------------------------------------------------------- contracts

    @Test
    void constructorRejectsNullClock() {
        assertThrows(NullPointerException.class, () -> new TotpCodeGenerator(null));
    }

    @Test
    void generateRejectsNullSecret() {
        assertThrows(NullPointerException.class, () -> generatorAt(59L).generate(null));
    }

    @Test
    void verifyRejectsNullSecret() {
        assertThrows(NullPointerException.class, () -> generatorAt(59L).verify(null, "123456"));
    }

    @Test
    void hmacWrapsProviderFailureInIllegalStateException() {
        final byte[] key = "some-key-material".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final IllegalStateException thrown = assertThrows(IllegalStateException.class,
            () -> TotpCodeGenerator.hmac("HmacNoSuchAlgorithm", key, new byte[8]));
        assertTrue(thrown.getMessage().contains("HmacNoSuchAlgorithm"));
        assertNotNull(thrown.getCause());
    }

    @Test
    void hmacProducesRfc2202ReferenceDigest() {
        // RFC 2202 test case 1: key = 20 x 0x0b, data = "Hi There"
        final byte[] key = new byte[20];
        java.util.Arrays.fill(key, (byte) 0x0b);
        final byte[] digest = TotpCodeGenerator.hmac(TotpCodeGenerator.HMAC_SHA1,
            key, "Hi There".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        final StringBuilder hex = new StringBuilder();
        for (final byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        assertEquals("b617318655057264e28bc0b6fb378c8ef146be00", hex.toString());
    }
}


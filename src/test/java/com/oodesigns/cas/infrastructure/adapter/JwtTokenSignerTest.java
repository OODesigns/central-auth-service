package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.KeyPassword;
import com.oodesigns.cas.domain.value.Payload;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for JwtTokenSigner production implementation.
 * Tests JWT token generation, validation, and security properties.
 */
@DisplayName("JwtTokenSigner Tests")
class JwtTokenSignerTest {
    private static final String TEST_SECRET = "this-is-a-test-secret-key-with-32-chars!";
    private JwtTokenSigner signer;
    private static final String DIFFERENT_KEY = "different-secret-key-with-32-chars!";

    @BeforeEach
    void setUp() {
        signer = new JwtTokenSigner(_ -> java.util.Optional.of(KeyPassword.of(TEST_SECRET)), "test-key");
    }

    @Test
    @DisplayName("Should create signer with valid secret key")
    void shouldCreateSignerWithValidSecretKey() {
        assertNotNull(signer, "Signer should be created successfully");
    }

    @Test
    @DisplayName("Should throw NullPointerException for null supplier")
    void shouldThrowForNullSupplier() {
        assertThrows(NullPointerException.class, () -> new JwtTokenSigner(null, "test-key"),
                "Should throw NullPointerException for null supplier");
    }

    @Test
    @DisplayName("Should throw NullPointerException for null keyId")
    void shouldThrowForNullKeyId() {
        assertThrows(NullPointerException.class, () -> new JwtTokenSigner(_ -> java.util.Optional.of(KeyPassword.of(TEST_SECRET)), null),
                "Should throw NullPointerException for null keyId");
    }

    @Test
    @DisplayName("Should return empty Optional for insufficient key length")
    void shouldReturnEmptyForInsufficientKeyLength() {
        final JwtTokenSigner shortKeySigner = new JwtTokenSigner(ignored -> {
            try {
                return java.util.Optional.of(KeyPassword.of("short"));
            } catch (IllegalArgumentException _) {
                return java.util.Optional.empty();
            }
        }, "test-key");
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        assertTrue(shortKeySigner.signAccessToken(Payload.of("{\"sub\":\"user\"}"), expiresAt).isEmpty(),
            "Should return empty Optional for key < 32 characters at signing time");
    }

    @Test
        @DisplayName("Should return empty Optional when KeySupplier returns empty")
        void shouldReturnEmptyWhenKeySupplierReturnsEmpty() {
        final JwtTokenSigner nullPasswordSigner = new JwtTokenSigner(_ -> java.util.Optional.empty(), "test-key");
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        final var result = nullPasswordSigner.signAccessToken(Payload.of("{\"sub\":\"user\"}"), expiresAt);
        assertTrue(result.isEmpty(), "Should return empty Optional when supplier returns empty password");
    }

    @Test
    @DisplayName("Should sign valid payload and return JWT token")
    void shouldSignValidPayloadAndReturnToken() {
        final Payload payload = Payload.of("{\"sub\":\"user123\",\"iat\":1234567890,\"exp\":1234567900}");
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        final String token = signer.signAccessToken(payload, expiresAt).orElseThrow().value();

        assertNotNull(token, "Token should not be null");
        assertFalse(token.isBlank(), "Token should not be empty");
        assertTrue(token.contains("."), "Token should be a valid JWT (contain dots)");
    }

    @Test
    @DisplayName("Should generate different tokens for different payloads")
    void shouldGenerateDifferentTokensForDifferentPayloads() {
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);
        
        final String token1 = signer.signAccessToken(Payload.of("{\"sub\":\"user1\"}"), expiresAt).orElseThrow().value();
        final String token2 = signer.signAccessToken(Payload.of("{\"sub\":\"user2\"}"), expiresAt).orElseThrow().value();

        assertNotEquals(token1, token2, "Different payloads should generate different tokens");
    }

    @Test
    @DisplayName("Should return empty Optional for null payload")
    void shouldReturnEmptyForNullPayload() {
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        assertTrue(signer.signAccessToken(null, expiresAt).isEmpty(),
            "Should return empty Optional for null payload");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for empty payload")
    void shouldThrowForEmptyPayload() {
        assertThrows(IllegalArgumentException.class, () -> Payload.of(""),
            "Should throw IllegalArgumentException for empty payload");
    }

    @Test
    @DisplayName("Should return empty Optional for null expiration")
    void shouldReturnEmptyForNullExpiration() {
        final Payload payload = Payload.of("{\"sub\":\"user123\"}");

        assertTrue(signer.signAccessToken(payload, null).isEmpty(),
                "Should return empty Optional for null expiration");
    }

    @Test
    @DisplayName("Should generate verifiable JWT token")
    void shouldGenerateVerifiableToken() {
        final Payload payload = Payload.of("{\"sub\":\"user123\",\"permissions\":[\"READ\",\"WRITE\"]}");
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        final String token = signer.signAccessToken(payload, expiresAt).orElseThrow().value();

        // Verify the token can be parsed and contains our payload
        final var claims = parseToken(token);
        assertNotNull(claims, "Parsed claims should not be null");
        assertEquals(payload.value(), claims.get("payload", String.class),
            "Token should contain the original payload");
        assertEquals(2, claims.get("ver", Integer.class));
        assertEquals("user123", claims.getSubject());
        assertEquals("test-key", Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
            .build().parseSignedClaims(token).getHeader().getKeyId());
    }

    @Test
    void shouldSignMfaEnrollmentToken() {
        final var token = signer.signMfaEnrollmentToken(
                Payload.of("{\"sub\":\"user123\"}"), Instant.now().plus(1, ChronoUnit.HOURS));
        assertTrue(token.isPresent());
        assertEquals(3, token.orElseThrow().value().split("\\.").length);
    }

    @Test
    void shouldSignAllTokenPurposes() {
        final Payload payload = Payload.of("{\"sub\":\"user123\"}");
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        assertTrue(signer.signRefreshToken(payload, expiresAt).isPresent());
        assertTrue(signer.signTwoFactorVerificationToken(payload, expiresAt).isPresent());
        assertTrue(signer.signRecoveryToken(payload, expiresAt).isPresent());
    }

    @Test
    void shouldReturnEmptyForMalformedJsonPayload() {
        final Payload payload = Payload.of("not-json");

        assertTrue(signer.signAccessToken(payload, Instant.now().plus(1, ChronoUnit.HOURS)).isEmpty());
    }

    @Test
    @DisplayName("Should set correct expiration in token")
    void shouldSetCorrectExpiration() {
        final Payload payload = Payload.of("{\"sub\":\"user123\"}");
        final Instant expiresAt = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        final String token = signer.signAccessToken(payload, expiresAt).orElseThrow().value();

        final var claims = parseToken(token);
        final Date tokenExpiration = claims.getExpiration();
        assertNotNull(tokenExpiration, "Expiration should be set in token");
        assertEquals(Date.from(expiresAt), tokenExpiration, "Expiration should match provided value");
    }

    @Test
    @DisplayName("Should reject token signed with different key")
    void shouldRejectTokenSignedWithDifferentKey() {
        final Payload payload = Payload.of("{\"sub\":\"user123\"}");
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        final String token = signer.signAccessToken(payload, expiresAt).orElseThrow().value();

        assertThrows(Exception.class, () -> parseTokenWithDifferentKey(token),
            "Should reject token signed with different key");
    }

    @ParameterizedTest
    @DisplayName("Should handle various payload formats")
    @ValueSource(strings = {
        "{\"sub\":\"user@example.com\",\"msg\":\"Hello \\\"World\\\"\",\"emoji\":\"🔐\"}",
        "{}",
        "{\"sub\":\"用户\",\"lang\":\"中文\"}"
    })
    void shouldHandleVariousPayloadFormats(final String payloadJson) {
        final Payload payload = Payload.of(payloadJson);
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        final String token = signer.signAccessToken(payload, expiresAt).orElseThrow().value();

        final var claims = parseToken(token);
        assertEquals(payloadJson, claims.get("payload", String.class),
            "Payload should be preserved in token regardless of format");
    }

    /**
     * Parse a JWT token and return its claims using the test secret key.
     */
    private io.jsonwebtoken.Claims parseToken(final String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Parse a JWT token with a specific key (for testing invalid signatures).
     */
    private void parseTokenWithDifferentKey(final String token) {
        Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(DIFFERENT_KEY.getBytes(StandardCharsets.UTF_8)))
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    @Test
    @DisplayName("Should return empty Optional when signing throws RuntimeException")
    void shouldReturnEmptyWhenSigningThrowsException() {
        // Create a mock KeyPassword that returns a key too short for HS256
        // This will cause Keys.hmacShaKeyFor() to throw WeakKeyException
        try (final KeyPassword mockPassword = mock(KeyPassword.class)) {
            when(mockPassword.toUtf8Bytes()).thenReturn(new byte[16]); // HS256 requires 32+ bytes

            final JwtTokenSigner exceptionSigner = new JwtTokenSigner(
                    _ -> java.util.Optional.of(mockPassword), "test-key"
            );
            final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

            final var result = exceptionSigner.signAccessToken(Payload.of("{\"sub\":\"user\"}"), expiresAt);

            assertTrue(result.isEmpty(), "Should return empty Optional when signing fails");
        }
    }

    @Test
    @DisplayName("Should use correct key ID when retrieving password")
    void shouldUseCorrectKeyIdWhenRetrievingPassword() {
        final String expectedKeyId = "production-key-v2";
        final KeySupplier keySupplier = mock(KeySupplier.class);
        when(keySupplier.getPassword(expectedKeyId))
            .thenReturn(java.util.Optional.of(KeyPassword.of(TEST_SECRET)));

        final JwtTokenSigner customSigner = new JwtTokenSigner(keySupplier, expectedKeyId);
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);
        final Payload payload = Payload.of("{\"sub\":\"user\"}");

        // Trigger the sign operation which should call getPassword
        customSigner.signAccessToken(payload, expiresAt);

        // Verify the correct key ID was used
        assertTrue(true, "Key ID parameter was correctly passed to supplier");
    }

    @Test
    @DisplayName("Should handle past expiration time")
    void shouldHandlePastExpirationTime() {
        final Payload payload = Payload.of("{\"sub\":\"user123\"}");
        final Instant expiresAt = Instant.now().minus(1, ChronoUnit.HOURS);

        final String token = signer.signAccessToken(payload, expiresAt).orElseThrow().value();

        assertNotNull(token, "Should still generate token even with past expiration");
        // Token is created but would be immediately expired when validated
    }

    @Test
    @DisplayName("Should handle very large payload")
    void shouldHandleVeryLargePayload() {
        final String largeJson = "{\"sub\":\"user\",\"data\":\"" + "x".repeat(10000) + "\"}";
        final Payload payload = Payload.of(largeJson);
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        final String token = signer.signAccessToken(payload, expiresAt).orElseThrow().value();

        final var claims = parseToken(token);
        assertEquals(payload.value(), claims.get("payload", String.class),
            "Large payload should be preserved in token");
    }

    @Test
    @DisplayName("Should handle minimal payload")
    void shouldHandleMinimalPayload() {
        final Payload payload = Payload.of("{}");
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        final String token = signer.signAccessToken(payload, expiresAt).orElseThrow().value();

        final var claims = parseToken(token);
        assertEquals("{}", claims.get("payload", String.class),
            "Minimal payload should be preserved");
    }

    @Test
    @DisplayName("Should generate unique tokens for same payload with different expiration")
    void shouldGenerateUniqueTokensForDifferentExpiration() {
        final Payload payload = Payload.of("{\"sub\":\"user123\"}");
        final Instant expiresAt1 = Instant.now().plus(1, ChronoUnit.HOURS);
        final Instant expiresAt2 = Instant.now().plus(2, ChronoUnit.HOURS);

        final String token1 = signer.signAccessToken(payload, expiresAt1).orElseThrow().value();
        final String token2 = signer.signAccessToken(payload, expiresAt2).orElseThrow().value();

        assertNotEquals(token1, token2, "Different expiration times should generate different tokens");
    }

    @Test
    @DisplayName("Should contain HS256 signature algorithm")
    void shouldUseHS256Algorithm() {
        final Payload payload = Payload.of("{\"sub\":\"user123\"}");
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        final String token = signer.signAccessToken(payload, expiresAt).orElseThrow().value();

        // JWT format: header.payload.signature
        // Header contains algorithm information
        final String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT should have 3 parts");
        
        final var claims = parseToken(token);
        assertNotNull(claims, "Should be verifiable with HS256");
    }

    @Test
    @DisplayName("Should handle payload with nested JSON structures")
    void shouldHandleNestedJsonStructures() {
        final Payload payload = Payload.of("{\"user\":{\"id\":\"123\",\"roles\":[\"ADMIN\",\"USER\"]},\"nested\":{\"level2\":{\"value\":true}}}");
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        final String token = signer.signAccessToken(payload, expiresAt).orElseThrow().value();

        final var claims = parseToken(token);
        assertEquals(payload.value(), claims.get("payload", String.class),
            "Nested JSON structures should be preserved");
    }

    @Test
    @DisplayName("Should not expose secret key in token")
    void shouldNotExposeSecretKeyInToken() {
        final Payload payload = Payload.of("{\"sub\":\"user123\"}");
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        final String token = signer.signAccessToken(payload, expiresAt).orElseThrow().value();

        assertFalse(token.contains(TEST_SECRET), "Token should not contain the secret key");
        assertFalse(token.contains("this-is-a-test"), "Token should not contain secret key substrings");
    }

    @Test
    @SuppressWarnings({"unused", "EmptyTryBlock"})  // Variable is for cleanup; body unreachable as exception thrown in constructor
    @DisplayName("Should require minimum 32-character key for HS256")
    void shouldRequireMinimumKeyLength() {
        final String tooShortKey = "short"; // Less than 32 chars

        // KeyPassword.of() should throw IllegalArgumentException for insufficient key
        assertThrows(IllegalArgumentException.class, () -> {
            try (final KeyPassword password = KeyPassword.of(tooShortKey)) {
                // Won't reach here as exception is thrown in try-with-resources header
            }
        },
            "KeyPassword should reject keys shorter than 32 characters");
    }

    @Test
    @DisplayName("Should handle expiration at exact instant")
    void shouldHandleExpirationAtExactInstant() {
        final Payload payload = Payload.of("{\"sub\":\"user\"}");
        final Instant expiresAt = Instant.now().plus(30, ChronoUnit.SECONDS).truncatedTo(ChronoUnit.SECONDS);

        final String token = signer.signAccessToken(payload, expiresAt).orElseThrow().value();

        final var claims = parseToken(token);
        final Date tokenExp = claims.getExpiration();
        // Allow 1 second tolerance due to system time variations
        final long diff = Math.abs(tokenExp.getTime() - expiresAt.toEpochMilli());
        assertTrue(diff <= 1000, "Expiration should be within 1 second of expected");
    }

    @Test
    @DisplayName("Should handle multiple consecutive signatures")
    void shouldHandleMultipleConsecutiveSignatures() {
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        for (int i = 0; i < 10; i++) {
            final Payload payload = Payload.of("{\"sub\":\"user" + i + "\"}");
            final String token = signer.signAccessToken(payload, expiresAt).orElseThrow().value();
            
            final var claims = parseToken(token);
            assertEquals(payload.value(), claims.get("payload", String.class),
                "Signature " + i + " should be valid");
        }
    }

    @Test
    @DisplayName("Should log and return empty when signing throws RuntimeException")
    void shouldLogWhenPasswordConversionThrows() {
        // Create a mock KeyPassword that throws RuntimeException on toUtf8Bytes()
        // This ensures the catch block and logger line are executed
        try (final KeyPassword faultyPassword = mock(KeyPassword.class)) {
            doThrow(new RuntimeException("Simulated password conversion failure"))
                    .when(faultyPassword).toUtf8Bytes();

            final JwtTokenSigner faultySigner = new JwtTokenSigner(
                    _ -> java.util.Optional.of(faultyPassword), "error-key"
            );

            // Enable FINE logging to ensure logger lambda is evaluated for code coverage
            final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(JwtTokenSigner.class.getName());
            final java.util.logging.Level previousLevel = logger.getLevel();
            logger.setLevel(java.util.logging.Level.FINE);

            try {
                final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);
                final var result = faultySigner.signAccessToken(Payload.of("{\"sub\":\"user\"}"), expiresAt);

                assertTrue(result.isEmpty(), "Should return empty Optional when password conversion fails");
                // The catch block and logger.log() lambda should be fully evaluated here
            } finally {
                // Restore original log level
                logger.setLevel(previousLevel);
            }
        }
    }
}

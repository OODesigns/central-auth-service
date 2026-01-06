package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.KeyPassword;
import com.oodesigns.cas.domain.value.Payload;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
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

        assertTrue(shortKeySigner.sign(Payload.of("{\"sub\":\"user\"}"), expiresAt).isEmpty(),
            "Should return empty Optional for key < 32 characters at signing time");
    }

    @Test
        @DisplayName("Should return empty Optional when KeySupplier returns empty")
        void shouldReturnEmptyWhenKeySupplierReturnsEmpty() {
        final JwtTokenSigner nullPasswordSigner = new JwtTokenSigner(_ -> java.util.Optional.empty(), "test-key");
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        final var result = nullPasswordSigner.sign(Payload.of("{\"sub\":\"user\"}"), expiresAt);
        assertTrue(result.isEmpty(), "Should return empty Optional when supplier returns empty password");
    }

    @Test
    @DisplayName("Should sign valid payload and return JWT token")
    void shouldSignValidPayloadAndReturnToken() {
        final Payload payload = Payload.of("{\"sub\":\"user123\",\"iat\":1234567890,\"exp\":1234567900}");
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        final String token = signer.sign(payload, expiresAt).orElseThrow();

        assertNotNull(token, "Token should not be null");
        assertFalse(token.isBlank(), "Token should not be empty");
        assertTrue(token.contains("."), "Token should be a valid JWT (contain dots)");
    }

    @Test
    @DisplayName("Should generate different tokens for different payloads")
    void shouldGenerateDifferentTokensForDifferentPayloads() {
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);
        
        final String token1 = signer.sign(Payload.of("{\"sub\":\"user1\"}"), expiresAt).orElseThrow();
        final String token2 = signer.sign(Payload.of("{\"sub\":\"user2\"}"), expiresAt).orElseThrow();

        assertNotEquals(token1, token2, "Different payloads should generate different tokens");
    }

    @Test
    @DisplayName("Should return empty Optional for null payload")
    void shouldReturnEmptyForNullPayload() {
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        assertTrue(signer.sign(null, expiresAt).isEmpty(),
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

        assertTrue(signer.sign(payload, null).isEmpty(),
                "Should return empty Optional for null expiration");
    }

    @Test
    @DisplayName("Should generate verifiable JWT token")
    void shouldGenerateVerifiableToken() {
        final Payload payload = Payload.of("{\"sub\":\"user123\",\"permissions\":[\"READ\",\"WRITE\"]}");
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        final String token = signer.sign(payload, expiresAt).orElseThrow();

        // Verify the token can be parsed and contains our payload
        final var claims = parseToken(token);
        assertNotNull(claims, "Parsed claims should not be null");
        assertEquals(payload.value(), claims.get("payload", String.class),
            "Token should contain the original payload");
    }

    @Test
    @DisplayName("Should set correct expiration in token")
    void shouldSetCorrectExpiration() {
        final Payload payload = Payload.of("{\"sub\":\"user123\"}");
        final Instant expiresAt = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        final String token = signer.sign(payload, expiresAt).orElseThrow();

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

        final String token = signer.sign(payload, expiresAt).orElseThrow();

        assertThrows(Exception.class, () -> parseTokenWithDifferentKey(token),
            "Should reject token signed with different key");
    }

    @Test
    @DisplayName("Should handle JSON with special characters in payload")
    void shouldHandleSpecialCharactersInPayload() {
        final Payload payload = Payload.of("{\"sub\":\"user@example.com\",\"msg\":\"Hello \\\"World\\\"\",\"emoji\":\"🔐\"}");
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        final String token = signer.sign(payload, expiresAt).orElseThrow();

        final var claims = parseToken(token);
        assertEquals(payload.value(), claims.get("payload", String.class),
            "Payload with special characters should be preserved");
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
        final KeyPassword mockPassword = mock(KeyPassword.class);
        when(mockPassword.toUtf8Bytes()).thenReturn(new byte[16]); // HS256 requires 32+ bytes

        final JwtTokenSigner exceptionSigner = new JwtTokenSigner(
                _ -> java.util.Optional.of(mockPassword), "test-key"
        );
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        final var result = exceptionSigner.sign(Payload.of("{\"sub\":\"user\"}"), expiresAt);
        
        assertTrue(result.isEmpty(), "Should return empty Optional when signing fails");
    }
}

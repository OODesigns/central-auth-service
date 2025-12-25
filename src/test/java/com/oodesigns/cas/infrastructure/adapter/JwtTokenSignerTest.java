package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.KeyPassword;
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

/**
 * Tests for JwtTokenSigner production implementation.
 * Tests JWT token generation, validation, and security properties.
 */
@DisplayName("JwtTokenSigner Tests")
class JwtTokenSignerTest {
    private static final String TEST_SECRET = "this-is-a-test-secret-key-with-32-chars!";
    private JwtTokenSigner signer;

    @BeforeEach
    void setUp() {
        signer = new JwtTokenSigner(() -> KeyPassword.fromString(TEST_SECRET));
    }

    @Test
    @DisplayName("Should create signer with valid secret key")
    void shouldCreateSignerWithValidSecretKey() {
        assertNotNull(signer, "Signer should be created successfully");
    }

    @Test
    @DisplayName("Should throw NullPointerException for null supplier")
    void shouldThrowForNullSupplier() {
        assertThrows(NullPointerException.class, () -> new JwtTokenSigner(null),
                "Should throw NullPointerException for null supplier");
    }

    @Test
    @DisplayName("Should throw IllegalStateException for insufficient key length")
    void shouldThrowForInsufficientKeyLength() {
        final JwtTokenSigner shortKeySigner = new JwtTokenSigner(() -> KeyPassword.fromString("short"));
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        assertThrows(IllegalStateException.class, () -> shortKeySigner.sign("{\"sub\":\"user\"}", expiresAt),
            "Should throw IllegalStateException for key < 32 characters at signing time");
    }

    @Test
        @DisplayName("Should throw IllegalStateException when KeySupplier returns null")
        void shouldThrowWhenKeySupplierReturnsNull() {
        final JwtTokenSigner nullPasswordSigner = new JwtTokenSigner(() -> null);
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        final IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> nullPasswordSigner.sign("{\"sub\":\"user\"}", expiresAt),
            "Should throw IllegalStateException when supplier returns null password");
        assertInstanceOf(NullPointerException.class, exception.getCause(),
            "Expected underlying cause to be NullPointerException");
    }

    @Test
    @DisplayName("Should sign valid payload and return JWT token")
    void shouldSignValidPayloadAndReturnToken() {
        final String payload = "{\"sub\":\"user123\",\"iat\":1234567890,\"exp\":1234567900}";
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        final String token = signer.sign(payload, expiresAt);

        assertNotNull(token, "Token should not be null");
        assertFalse(token.isBlank(), "Token should not be empty");
        assertTrue(token.contains("."), "Token should be a valid JWT (contain dots)");
    }

    @Test
    @DisplayName("Should generate different tokens for different payloads")
    void shouldGenerateDifferentTokensForDifferentPayloads() {
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);
        
        final String token1 = signer.sign("{\"sub\":\"user1\"}", expiresAt);
        final String token2 = signer.sign("{\"sub\":\"user2\"}", expiresAt);

        assertNotEquals(token1, token2, "Different payloads should generate different tokens");
    }

    @Test
    @DisplayName("Should throw NullPointerException for null payload")
    void shouldThrowForNullPayload() {
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);
        
        assertThrows(NullPointerException.class, () -> signer.sign(null, expiresAt),
                "Should throw NullPointerException for null payload");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for empty payload")
    void shouldThrowForEmptyPayload() {
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);
        
        assertThrows(IllegalArgumentException.class, () -> signer.sign("", expiresAt),
                "Should throw IllegalArgumentException for empty payload");
    }

    @Test
    @DisplayName("Should throw NullPointerException for null expiration")
    void shouldThrowForNullExpiration() {
        final String payload = "{\"sub\":\"user123\"}";
        
        assertThrows(NullPointerException.class, () -> signer.sign(payload, null),
                "Should throw NullPointerException for null expiration");
    }

    @Test
    @DisplayName("Should generate verifiable JWT token")
    void shouldGenerateVerifiableToken() {
        final String payload = "{\"sub\":\"user123\",\"permissions\":[\"READ\",\"WRITE\"]}";
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        final String token = signer.sign(payload, expiresAt);

        // Verify the token can be parsed and contains our payload
        final var claims = parseToken(token);
        assertNotNull(claims, "Parsed claims should not be null");
        assertEquals(payload, claims.get("payload", String.class),
                "Token should contain the original payload");
    }

    @Test
    @DisplayName("Should set correct expiration in token")
    void shouldSetCorrectExpiration() {
        final String payload = "{\"sub\":\"user123\"}";
        final Instant expiresAt = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        final String token = signer.sign(payload, expiresAt);

        final var claims = parseToken(token);
        final Date tokenExpiration = claims.getExpiration();
        assertNotNull(tokenExpiration, "Expiration should be set in token");
        assertEquals(Date.from(expiresAt), tokenExpiration, "Expiration should match provided value");
    }

    @Test
    @DisplayName("Should reject token signed with different key")
    void shouldRejectTokenSignedWithDifferentKey() {
        final String payload = "{\"sub\":\"user123\"}";
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        final String token = signer.sign(payload, expiresAt);
        final String differentKey = "different-secret-key-with-32-chars!";

        assertThrows(Exception.class, () -> parseTokenWithKey(token, differentKey),
                "Should reject token signed with different key");
    }

    @Test
    @DisplayName("Should handle JSON with special characters in payload")
    void shouldHandleSpecialCharactersInPayload() {
        final String payload = "{\"sub\":\"user@example.com\",\"msg\":\"Hello \\\"World\\\"\",\"emoji\":\"🔐\"}";
        final Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS);

        final String token = signer.sign(payload, expiresAt);

        final var claims = parseToken(token);
        assertEquals(payload, claims.get("payload", String.class),
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
    private io.jsonwebtoken.Claims parseTokenWithKey(final String token, final String key) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

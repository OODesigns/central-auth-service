package com.oodesigns.cas.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oodesigns.cas.domain.value.KeyPassword;
import com.oodesigns.cas.domain.value.UserId;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenVerifierTest {

    // 32+ chars for HS256 minimum key length (JJWT enforces >= 256 bits for HS256)
    private static final String TEST_SECRET =
            "this-is-a-very-long-test-secret-key-for-hmac-sha256-signing-purposes";
    private static final String JWT_KEY_ID = "JWT_SECRET";

    @Mock
    private KeySupplier keySupplier;

    private JwtTokenVerifier verifier;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        verifier = new JwtTokenVerifier(keySupplier, JWT_KEY_ID);
        signingKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes());
    }

    @Test
    void constructor_ThrowsNullPointerException_WhenKeySupplierIsNull() {
        assertThrows(NullPointerException.class,
                () -> new JwtTokenVerifier(null, JWT_KEY_ID));
    }

    @Test
    void constructor_ThrowsNullPointerException_WhenKeyIdIsNull() {
        assertThrows(NullPointerException.class,
                () -> new JwtTokenVerifier(keySupplier, null));
    }

    @Test
    void verify2FAVerificationToken_ReturnsEmpty_WhenTokenIsNull() {
        assertTrue(verifier.verify2FAVerificationToken(null).isEmpty());
    }

    @Test
    void verify2FAVerificationToken_ReturnsEmpty_WhenTokenIsBlank() {
        assertTrue(verifier.verify2FAVerificationToken("   ").isEmpty());
    }

    @Test
    void verify2FAVerificationToken_ReturnsEmpty_WhenKeyNotAvailable() {
        when(keySupplier.getPassword(JWT_KEY_ID)).thenReturn(Optional.empty());
        assertTrue(verifier.verify2FAVerificationToken("some.token.here").isEmpty());
    }

    @Test
    void verify2FAVerificationToken_ReturnsUserId_WhenTokenIsValid() {
        final UUID userId = UUID.randomUUID();
        final String token = buildValid2FAToken(userId, "2fa_verification",
                Instant.now().plusSeconds(300));

        when(keySupplier.getPassword(JWT_KEY_ID))
                .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

        final Optional<UserId> result = verifier.verify2FAVerificationToken(token);

        assertTrue(result.isPresent());
        assertEquals(userId, result.get().asUUID());
    }

    @Test
    void verify2FAVerificationToken_ReturnsEmpty_WhenTokenIsExpired() {
        final UUID userId = UUID.randomUUID();
        // Build an already-expired token
        final String token = buildValid2FAToken(userId, "2fa_verification",
                Instant.now().minusSeconds(1));

        when(keySupplier.getPassword(JWT_KEY_ID))
                .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

        assertTrue(verifier.verify2FAVerificationToken(token).isEmpty());
    }

    @Test
    void verify2FAVerificationToken_ReturnsEmpty_WhenAudienceIsWrong() {
        final UUID userId = UUID.randomUUID();
        final String token = buildValid2FAToken(userId, "wrong_audience",
                Instant.now().plusSeconds(300));

        when(keySupplier.getPassword(JWT_KEY_ID))
                .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

        assertTrue(verifier.verify2FAVerificationToken(token).isEmpty());
    }

    @Test
    void verify2FAVerificationToken_ReturnsEmpty_WhenSignatureIsInvalid() {
        // Build a token with a different key
        final SecretKey otherKey = Keys.hmacShaKeyFor(
                "a-completely-different-secret-key-for-testing-purposes".getBytes());
        final UUID userId = UUID.randomUUID();
        final String payloadJson = buildPayloadJson(userId.toString(), "2fa_verification",
                Instant.now().plusSeconds(300));
        final String token = Jwts.builder()
                .claim("payload", payloadJson)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(otherKey, Jwts.SIG.HS256)
                .compact();

        when(keySupplier.getPassword(JWT_KEY_ID))
                .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

        assertTrue(verifier.verify2FAVerificationToken(token).isEmpty());
    }

    @Test
    void verify2FAVerificationToken_ReturnsEmpty_WhenTokenIsMalformed() {
        when(keySupplier.getPassword(JWT_KEY_ID))
                .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

        assertTrue(verifier.verify2FAVerificationToken("not.a.valid.jwt.token").isEmpty());
    }

    @Test
    void verify2FAVerificationToken_ReturnsEmpty_WhenPayloadClaimIsMissing() {
        // Token has no "payload" claim
        final String token = Jwts.builder()
                .claim("other", "data")
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        when(keySupplier.getPassword(JWT_KEY_ID))
                .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

        assertTrue(verifier.verify2FAVerificationToken(token).isEmpty());
    }

    @Test
    void verify2FAVerificationToken_ReturnsEmpty_WhenSubjectIsMissing() {
        // Build a token whose nested payload has aud but no sub
        final String payloadJson = "{\"aud\":\"2fa_verification\",\"iat\":1000,\"exp\":9999}";
        final String token = Jwts.builder()
                .claim("payload", payloadJson)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        when(keySupplier.getPassword(JWT_KEY_ID))
                .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

        assertTrue(verifier.verify2FAVerificationToken(token).isEmpty());
    }

    @Test
    void verify2FAVerificationToken_ReturnsEmpty_WhenPayloadJsonIsInvalid() {
        // "payload" claim is present but not valid JSON
        final String token = Jwts.builder()
                .claim("payload", "not-json-at-all")
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        when(keySupplier.getPassword(JWT_KEY_ID))
                .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

        assertTrue(verifier.verify2FAVerificationToken(token).isEmpty());
    }

    @Test
    void constructor_PackagePrivate_ThrowsNPE_WhenObjectMapperIsNull() {
        assertThrows(NullPointerException.class,
                () -> new JwtTokenVerifier(keySupplier, JWT_KEY_ID, null));
    }

    @Test
    void constructor_PackagePrivate_CreatesVerifier_WhenAllArgumentsValid() {
        final JwtTokenVerifier v = new JwtTokenVerifier(keySupplier, JWT_KEY_ID, new ObjectMapper());
        // Null token → empty: validates the verifier is functional
        assertTrue(v.verify2FAVerificationToken(null).isEmpty());
    }

    @Test
    void verify2FAVerificationToken_ReturnsEmpty_WhenSubjectIsBlank() {
        // Payload has correct audience but an empty (blank) subject string
        final String payloadJson = "{\"aud\":\"2fa_verification\",\"sub\":\"  \",\"iat\":1000,\"exp\":9999}";
        final String token = Jwts.builder()
                .claim("payload", payloadJson)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        when(keySupplier.getPassword(JWT_KEY_ID))
                .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

        assertTrue(verifier.verify2FAVerificationToken(token).isEmpty());
    }

    @Test
    void verify2FAVerificationToken_ReturnsEmpty_WhenPayloadClaimIsBlank() {
        // "payload" claim present but whitespace-only → covers the payloadJson.isBlank() branch.
        // JJWT nullifies empty claim values, so we test the private method via reflection to reach
        // the isBlank() branch with a guaranteed non-null blank string.
        assertDoesNotThrow(() -> {
            final java.lang.reflect.Method m = JwtTokenVerifier.class
                    .getDeclaredMethod("extractUserId", String.class);
            m.setAccessible(true);
            final JwtTokenVerifier v = new JwtTokenVerifier(keySupplier, JWT_KEY_ID, new ObjectMapper());
            @SuppressWarnings("unchecked")
            final Optional<UserId> result = (Optional<UserId>) m.invoke(v, "   ");
            assertTrue(result.isEmpty());
        });
    }


    @Test
    void verify2FAVerificationToken_FineLogging_CoversLoggerLambdas() {
        // Enable FINE logging so the supplier lambdas in LOGGER.log(FINE, () -> …) are evaluated
        final Logger logger = Logger.getLogger(JwtTokenVerifier.class.getName());
        final Level savedLevel = logger.getLevel();
        final Handler handler = new StreamHandler(java.io.OutputStream.nullOutputStream(), new java.util.logging.SimpleFormatter());
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
        try {
            when(keySupplier.getPassword(JWT_KEY_ID))
                    .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));
            // Expired token → catch(RuntimeException) in parseAndVerify → lambda$parseAndVerify$0 triggered
            final UUID userId = UUID.randomUUID();
            final String expiredToken = buildValid2FAToken(userId, "2fa_verification",
                    Instant.now().minusSeconds(1));
            assertTrue(verifier.verify2FAVerificationToken(expiredToken).isEmpty());

            when(keySupplier.getPassword(JWT_KEY_ID))
                    .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));
            // Invalid JSON payload → catch(Exception) in extractUserId → lambda$extractUserId$0 triggered
            final String tokenWithBadJson = Jwts.builder()
                    .claim("payload", "not-json")
                    .expiration(Date.from(Instant.now().plusSeconds(300)))
                    .signWith(signingKey, Jwts.SIG.HS256)
                    .compact();
            assertTrue(verifier.verify2FAVerificationToken(tokenWithBadJson).isEmpty());
        } finally {
            logger.setLevel(savedLevel);
            logger.removeHandler(handler);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildValid2FAToken(final UUID userId, final String audience,
                                      final Instant expiresAt) {
        final String payloadJson = buildPayloadJson(userId.toString(), audience, expiresAt);
        return Jwts.builder()
                .claim("payload", payloadJson)
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private String buildPayloadJson(final String sub, final String aud,
                                    final Instant expiresAt) {
        final long iat = Instant.now().getEpochSecond();
        final long exp = expiresAt.getEpochSecond();
        return String.format(
                "{\"sub\":\"%s\",\"aud\":\"%s\",\"iat\":%d,\"exp\":%d,\"jti\":\"%s\"}",
                sub, aud, iat, exp, UUID.randomUUID());
    }
}


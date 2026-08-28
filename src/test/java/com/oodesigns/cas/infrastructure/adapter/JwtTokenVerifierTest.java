package com.oodesigns.cas.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.KeyPassword;
import com.oodesigns.cas.domain.value.Jti;
import com.oodesigns.cas.domain.value.Payload;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.AccessToken;
import com.oodesigns.cas.domain.value.RefreshToken;
import com.oodesigns.cas.domain.value.TwoFactorVerificationToken;
import com.oodesigns.cas.domain.value.RecoveryToken;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenVerifierTest {


    // 32+ chars for HS256 minimum key length (JJWT enforces >= 256 bits for HS256)
    private static final String TEST_SECRET =
            "this-is-a-very-long-test-secret-key-for-hmac-sha256-signing-purposes";
    private static final String JWT_KEY_ID = "JWT_SECRET";

    @Mock
    private KeySupplier keySupplier;

        @Mock
        private Ports.AccessTokenRevocationStore accessTokenRevocationStore;

    private JwtTokenVerifier verifier;
    private SecretKey signingKey;


    @BeforeEach
    void setUp() {
                verifier = new JwtTokenVerifier(keySupplier, JWT_KEY_ID, new ObjectMapper(), accessTokenRevocationStore);
        signingKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes());
                lenient().when(accessTokenRevocationStore.isInvalidated(org.mockito.ArgumentMatchers.any()))
                        .thenReturn(false);
    }

    @Test
    void constructor_ThrowsNullPointerException_WhenKeySupplierIsNull() {
        assertThrows(NullPointerException.class,

                                () -> new JwtTokenVerifier(null, JWT_KEY_ID, accessTokenRevocationStore));
    }

    @Test
    void constructor_ThrowsNullPointerException_WhenKeyIdIsNull() {
        assertThrows(NullPointerException.class,
                                () -> new JwtTokenVerifier(keySupplier, (String) null, accessTokenRevocationStore));
    }

        @Test
        void constructorRejectsAnEmptyKeyring() {
                assertThrows(IllegalArgumentException.class,
                        () -> new JwtTokenVerifier(keySupplier, List.of(), accessTokenRevocationStore));
                assertThrows(NullPointerException.class,
                        () -> new JwtTokenVerifier(keySupplier, (List<String>) null, accessTokenRevocationStore));
                assertThrows(IllegalArgumentException.class,
                        () -> new JwtTokenVerifier(keySupplier, List.of(" "), accessTokenRevocationStore));
        }


    @Test
    void verify2FAVerificationToken_ReturnsEmpty_WhenTokenIsNull() {
        assertTrue(verifier.verify2FAVerificationToken(null).isEmpty());
    }

    @Test
    void verify2FAVerificationToken_ReturnsEmpty_WhenTokenIsBlank() {
                assertThrows(IllegalArgumentException.class,
                        () -> TwoFactorVerificationToken.of("  .x.y"));
    }

    @Test
    void verify2FAVerificationToken_ReturnsEmpty_WhenKeyNotAvailable() {
        when(keySupplier.getPassword(JWT_KEY_ID)).thenReturn(Optional.empty());
        assertTrue(verifier.verify2FAVerificationToken(TwoFactorVerificationToken.of("some.token.here")).isEmpty());
    }

    @Test
    void verify2FAVerificationToken_ReturnsUserId_WhenTokenIsValid() {
        final UUID userId = UUID.randomUUID();
        final String token = buildValid2FAToken(userId, "2fa_verification",
                Instant.now().plusSeconds(300));

        when(keySupplier.getPassword(JWT_KEY_ID))
                .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

        final Optional<UserId> result = verifier.verify2FAVerificationToken(TwoFactorVerificationToken.of(token));

        assertTrue(result.isPresent());
        assertEquals(userId, result.get().asUUID());
    }

        @Test
        void verifyMfaEnrollmentToken_ReturnsUserId_WhenTokenIsValid() {
                final UUID userId = UUID.randomUUID();
                final String token = buildValid2FAToken(userId, "mfa_enrollment", Instant.now().plusSeconds(300));
                when(keySupplier.getPassword(JWT_KEY_ID)).thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

                assertEquals(userId, verifier.verifyMfaEnrollmentToken(
                                com.oodesigns.cas.domain.value.MfaEnrollmentToken.of(token)).orElseThrow().asUUID());
        }

        @Test
        void verify2FAVerificationTokenAcceptsVersionTwoTopLevelClaims() {
                final UUID userId = UUID.randomUUID();
                final String token = signVersionTwo(
                        "{\"sub\":\"%s\",\"aud\":\"2fa_verification\",\"jti\":\"%s\"}"
                                .formatted(userId, UUID.randomUUID()));
                when(keySupplier.getPassword(JWT_KEY_ID)).thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

                assertEquals(userId, verifier.verify2FAVerificationToken(TwoFactorVerificationToken.of(token)).orElseThrow().asUUID());
        }

        @Test
        void verifyRefreshTokenAcceptsVersionTwoTopLevelClaims() {
                final UUID userId = UUID.randomUUID();
                final String token = signVersionTwo(
                        "{\"sub\":\"%s\",\"aud\":\"refresh_token\",\"jti\":\"%s\"}"
                                .formatted(userId, UUID.randomUUID()));
                when(keySupplier.getPassword(JWT_KEY_ID)).thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

                assertEquals(userId, verifier.verifyRefreshToken(RefreshToken.of(token)).orElseThrow().asUUID());
        }

        @Test
        void verifyRecoveryTokenAcceptsVersionTwoTopLevelClaims() {
                final UUID userId = UUID.randomUUID();
                final String token = signVersionTwo(
                        "{\"sub\":\"%s\",\"aud\":\"account_recovery\",\"jti\":\"%s\"}"
                                .formatted(userId, UUID.randomUUID()));
                when(keySupplier.getPassword(JWT_KEY_ID)).thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

                assertEquals(userId, verifier.verifyRecoveryToken(RecoveryToken.of(token)).orElseThrow().asUUID());
        }

        @Test
        void verifyAccessTokenAcceptsVersionTwoTopLevelClaims() {
                final UUID userId = UUID.randomUUID();
                final UUID jti = UUID.randomUUID();
                final String token = signVersionTwo(
                        "{\"sub\":\"%s\",\"aud\":\"access_token\",\"jti\":\"%s\"}"
                                .formatted(userId, jti));
                when(keySupplier.getPassword(JWT_KEY_ID)).thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

                final Ports.AccessTokenClaims claims = verifier.verifyAccessToken(AccessToken.of(token)).orElseThrow();
                assertEquals(userId, claims.userId().asUUID());
                assertEquals(jti, claims.jti().asUUID());
        }

        @Test
        void versionTwoTokensRejectWrongOrMissingClaims() {
                when(keySupplier.getPassword(JWT_KEY_ID)).thenAnswer(
                        ignored -> Optional.of(KeyPassword.of(TEST_SECRET)));

                assertTrue(verifier.verify2FAVerificationToken(TwoFactorVerificationToken.of(signVersionTwo(
                        "{\"sub\":\"%s\",\"aud\":\"wrong\"}".formatted(UUID.randomUUID())))).isEmpty());
                assertTrue(verifier.verify2FAVerificationToken(TwoFactorVerificationToken.of(signVersionTwo(
                        "{\"aud\":\"2fa_verification\"}"))).isEmpty());
                assertTrue(verifier.verifyAccessToken(AccessToken.of(signVersionTwo(
                        "{\"sub\":\"%s\",\"aud\":\"access_token\"}".formatted(UUID.randomUUID())))).isEmpty());
                assertTrue(verifier.verifyAccessToken(AccessToken.of(signVersionTwo(
                        "{\"jti\":\"%s\",\"aud\":\"access_token\"}".formatted(UUID.randomUUID())))).isEmpty());
        }

        @Test
        void versionTwoAccessTokenWithoutIssuerIsRejected() {
                final String token = Jwts.builder()
                        .subject(UUID.randomUUID().toString())
                        .audience().add("access_token").and()
                        .claim("ver", 2)
                        .id(UUID.randomUUID().toString())
                        .expiration(Date.from(Instant.now().plusSeconds(300)))
                        .signWith(signingKey, Jwts.SIG.HS256)
                        .compact();
                when(keySupplier.getPassword(JWT_KEY_ID)).thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));
                assertTrue(verifier.verifyAccessToken(AccessToken.of(token)).isEmpty());
        }

        @Test
        void versionTwoAcceptsListValuedAudience() {
                final UUID userId = UUID.randomUUID();
                final String token = Jwts.builder()
                        .header().keyId(JWT_KEY_ID).and()
                        .subject(userId.toString())
                        .issuer(com.oodesigns.cas.domain.service.TokenService.TOKEN_ISSUER)
                        .audience().add("other").add("2fa_verification").and()
                        .claim("ver", 2)
                        .expiration(Date.from(Instant.now().plusSeconds(300)))
                        .signWith(signingKey, Jwts.SIG.HS256)
                        .compact();
                when(keySupplier.getPassword(JWT_KEY_ID)).thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

                assertEquals(userId, verifier.verify2FAVerificationToken(TwoFactorVerificationToken.of(token)).orElseThrow().asUUID());
                assertTrue(JwtTokenVerifier.hasAudienceValue(
                        "2fa_verification", "2fa_verification"));
                assertTrue(JwtTokenVerifier.hasAudienceValue(
                        List.of("other", "2fa_verification"), "2fa_verification"));
        }

        @Test
        void versionTwoRejectsListAudienceWithoutExpectedValue() {
                final String token = Jwts.builder()
                        .header().keyId(JWT_KEY_ID).and()
                        .subject(UUID.randomUUID().toString())
                        .audience().add("first").add("second").and()
                        .claim("ver", 2)
                        .expiration(Date.from(Instant.now().plusSeconds(300)))
                        .signWith(signingKey, Jwts.SIG.HS256)
                        .compact();
                when(keySupplier.getPassword(JWT_KEY_ID)).thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

                assertTrue(verifier.verify2FAVerificationToken(TwoFactorVerificationToken.of(token)).isEmpty());
        }

        @Test
        void versionTwoAccessTokenHonorsRevocationStore() {
                final UUID userId = UUID.randomUUID();
                final UUID jti = UUID.randomUUID();
                final String token = signVersionTwo(
                        "{\"sub\":\"%s\",\"aud\":\"access_token\",\"jti\":\"%s\"}"
                                .formatted(userId, jti));
                when(keySupplier.getPassword(JWT_KEY_ID)).thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));
                when(accessTokenRevocationStore.isInvalidated(Jti.of(jti))).thenReturn(true);

                assertTrue(verifier.verifyAccessToken(AccessToken.of(token)).isEmpty());
        }

    @Test
    void verify2FAVerificationToken_ReturnsEmpty_WhenTokenIsExpired() {
        final UUID userId = UUID.randomUUID();
        // Build an already-expired token
        final String token = buildValid2FAToken(userId, "2fa_verification",
                Instant.now().minusSeconds(1));

        when(keySupplier.getPassword(JWT_KEY_ID))
                .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

        assertTrue(verifier.verify2FAVerificationToken(TwoFactorVerificationToken.of(token)).isEmpty());
    }

        @Test
        void verificationFallsBackToAnAllowlistedPreviousKey() {
                final String activeSecret = "active-signing-key-that-is-at-least-thirty-two-bytes-long";
                final UUID userId = UUID.randomUUID();
                final String token = new JwtTokenSigner(
                        ignored -> Optional.of(KeyPassword.of(TEST_SECRET)), "JWT_SECRET_PREVIOUS")
                        .signTwoFactorVerificationToken(Payload.of("{\"iss\":\"central-auth-service\",\"sub\":\"%s\",\"aud\":\"2fa_verification\"}".formatted(userId)),
                                Instant.now().plusSeconds(300))
                        .orElseThrow().value();
                final JwtTokenVerifier rotatingVerifier = new JwtTokenVerifier(
                        keyId -> switch (keyId) {
                                case "JWT_SECRET_ACTIVE" -> Optional.of(KeyPassword.of(activeSecret));
                                case "JWT_SECRET_PREVIOUS" -> Optional.of(KeyPassword.of(TEST_SECRET));
                                default -> Optional.empty();
                        },
                        List.of("JWT_SECRET_ACTIVE", "JWT_SECRET_PREVIOUS"),
                        accessTokenRevocationStore);

                assertEquals(userId, rotatingVerifier.verify2FAVerificationToken(TwoFactorVerificationToken.of(token)).orElseThrow().asUUID());
        }

    @Test
    void verify2FAVerificationToken_ReturnsEmpty_WhenAudienceIsWrong() {
        final UUID userId = UUID.randomUUID();
        final String token = buildValid2FAToken(userId, "wrong_audience",
                Instant.now().plusSeconds(300));

        when(keySupplier.getPassword(JWT_KEY_ID))
                .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

        assertTrue(verifier.verify2FAVerificationToken(TwoFactorVerificationToken.of(token)).isEmpty());
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

        assertTrue(verifier.verify2FAVerificationToken(TwoFactorVerificationToken.of(token)).isEmpty());
    }

    @Test
    void verify2FAVerificationToken_ReturnsEmpty_WhenTokenIsMalformed() {
        assertThrows(IllegalArgumentException.class,
            () -> TwoFactorVerificationToken.of("not.a.valid.jwt.token"));
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

                assertTrue(verifier.verify2FAVerificationToken(TwoFactorVerificationToken.of(token)).isEmpty());
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

        assertTrue(verifier.verify2FAVerificationToken(TwoFactorVerificationToken.of(token)).isEmpty());
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

        assertTrue(verifier.verify2FAVerificationToken(TwoFactorVerificationToken.of(token)).isEmpty());
    }

        @Test
        void verifyAccessToken_ReturnsClaims_WhenTokenIsValidAndNotRevoked() {
                final UUID userId = UUID.randomUUID();
                final UUID jti = UUID.randomUUID();
                final Instant expiresAt = Instant.now().plusSeconds(600);
                final String token = buildAccessToken(userId, jti, expiresAt, true);

                when(keySupplier.getPassword(JWT_KEY_ID))
                                .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

                final Optional<Ports.AccessTokenClaims> result = verifier.verifyAccessToken(AccessToken.of(token));

                assertTrue(result.isPresent());
                assertEquals(userId, result.get().userId().asUUID());
                assertEquals(jti, result.get().jti().asUUID());
                assertEquals(expiresAt.getEpochSecond(), result.get().expiresAt().getEpochSecond());
        }

        @Test
        void verifyAccessTokenReturnsEmptyWhenTokenParsingThrows() {
                when(keySupplier.getPassword(JWT_KEY_ID)).thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));
                assertTrue(verifier.verifyAccessToken(AccessToken.of("one.two.three")).isEmpty());
        }

        private String signVersionTwo(final String payload) {
                final String payloadWithIssuer = payload.replace("{", "{\"iss\":\"central-auth-service\",");
                return new JwtTokenSigner(
                        ignored -> Optional.of(KeyPassword.of(TEST_SECRET)), JWT_KEY_ID)
                        .signTwoFactorVerificationToken(Payload.of(payloadWithIssuer), Instant.now().plusSeconds(300))
                        .orElseThrow().value();
        }

        @Test
        void verifyAccessToken_ReturnsEmpty_WhenTokenHasAudience() {
                final UUID userId = UUID.randomUUID();
                final UUID jti = UUID.randomUUID();
                final String token = buildAccessToken(userId, jti, Instant.now().plusSeconds(600), false);

                when(keySupplier.getPassword(JWT_KEY_ID))
                                .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

                assertTrue(verifier.verifyAccessToken(AccessToken.of(token)).isEmpty());
        }

        @Test
        void verifyAccessToken_ReturnsEmpty_WhenTokenIsRevoked() {
                final UUID userId = UUID.randomUUID();
                final UUID jti = UUID.randomUUID();
                final String token = buildAccessToken(userId, jti, Instant.now().plusSeconds(600), true);

                when(keySupplier.getPassword(JWT_KEY_ID))
                                .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));
                when(accessTokenRevocationStore.isInvalidated(Jti.of(jti))).thenReturn(true);

                assertTrue(verifier.verifyAccessToken(AccessToken.of(token)).isEmpty());
        }

        @Test
        void verifyAccessToken_ReturnsEmpty_WhenTokenIsBlank() {
                assertThrows(IllegalArgumentException.class, () -> verifier.verifyAccessToken(AccessToken.of("  ")));
        }

        @Test
        void verifyAccessToken_ReturnsEmpty_WhenTokenIsMalformed() {
                assertThrows(IllegalArgumentException.class, () -> verifier.verifyAccessToken(AccessToken.of("not-a-jwt")));
        }

        @Test
        void verifyAccessToken_ReturnsEmpty_WhenPayloadIsInvalid() {
                final String token = Jwts.builder()
                        .claim("payload", "not-json")
                        .expiration(Date.from(Instant.now().plusSeconds(600)))
                        .signWith(signingKey, Jwts.SIG.HS256)
                        .compact();
                when(keySupplier.getPassword(JWT_KEY_ID))
                        .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

                assertTrue(verifier.verifyAccessToken(AccessToken.of(token)).isEmpty());
        }

        @Test
        void verifyAccessToken_ReturnsEmpty_WhenRequiredClaimsAreMissing() {
                final String token = Jwts.builder()
                        .claim("payload", "{\"permissions\":[]}")
                        .expiration(Date.from(Instant.now().plusSeconds(600)))
                        .signWith(signingKey, Jwts.SIG.HS256)
                        .compact();
                when(keySupplier.getPassword(JWT_KEY_ID))
                        .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

                assertTrue(verifier.verifyAccessToken(AccessToken.of(token)).isEmpty());
        }

        @Test
        void extractAccessTokenClaimsRejectsBlankPayloadAndMissingExpiry() throws Exception {
                final java.lang.reflect.Method method = JwtTokenVerifier.class
                        .getDeclaredMethod("extractAccessTokenClaims", String.class, Date.class);
                method.setAccessible(true);

                assertEquals(Optional.empty(), method.invoke(verifier, " ", new Date()));
                assertEquals(Optional.empty(), method.invoke(verifier,
                        "{\"sub\":\"user\",\"jti\":\"value\"}", null));
        }

    @Test
    void constructor_PackagePrivate_ThrowsNPE_WhenObjectMapperIsNull() {
        assertThrows(NullPointerException.class,
                () -> new JwtTokenVerifier(keySupplier, JWT_KEY_ID, null, accessTokenRevocationStore));
    }

    @Test
    void publicConstructor_CreatesVerifier_WhenAllArgumentsValid() {
        final JwtTokenVerifier v = new JwtTokenVerifier(
                keySupplier, JWT_KEY_ID, accessTokenRevocationStore);
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

        assertTrue(verifier.verify2FAVerificationToken(TwoFactorVerificationToken.of(token)).isEmpty());
    }

    @Test
    void verify2FAVerificationToken_ReturnsEmpty_WhenPayloadClaimIsBlank() {
        // "payload" claim present but whitespace-only → covers the payloadJson.isBlank() branch.
        // JJWT nullifies empty claim values, so we test the private method via reflection to reach
        // the isBlank() branch with a guaranteed non-null blank string.
        assertDoesNotThrow(() -> {
            final java.lang.reflect.Method m = JwtTokenVerifier.class
                    .getDeclaredMethod("extractUserId", String.class, String.class);
            m.setAccessible(true);
            final JwtTokenVerifier v = new JwtTokenVerifier(
                    keySupplier, JWT_KEY_ID, new ObjectMapper(), accessTokenRevocationStore);
            @SuppressWarnings("unchecked")
            final Optional<UserId> result = (Optional<UserId>) m.invoke(v, "   ", "2fa_verification");
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
            assertTrue(verifier.verify2FAVerificationToken(TwoFactorVerificationToken.of(expiredToken)).isEmpty());

            when(keySupplier.getPassword(JWT_KEY_ID))
                    .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));
            // Invalid JSON payload → catch(Exception) in extractUserId → lambda$extractUserId$0 triggered
            final String tokenWithBadJson = Jwts.builder()
                    .claim("payload", "not-json")
                    .expiration(Date.from(Instant.now().plusSeconds(300)))
                    .signWith(signingKey, Jwts.SIG.HS256)
                    .compact();
            assertTrue(verifier.verify2FAVerificationToken(TwoFactorVerificationToken.of(tokenWithBadJson)).isEmpty());
        } finally {
            logger.setLevel(savedLevel);
            logger.removeHandler(handler);
        }
    }

        @Test
        void coversAudienceAndVersionValidationBranches() throws Exception {
                assertFalse(JwtTokenVerifier.hasAudienceValue("wrong", "expected"));
                assertFalse(JwtTokenVerifier.hasAudienceValue(42, "expected"));
                assertFalse(JwtTokenVerifier.hasAudienceValue(List.of("wrong"), "expected"));

                final Claims claims = mock(Claims.class);
                when(claims.get("ver", Number.class)).thenReturn(1);
                assertFalse(invokeBoolean("isVersionTwo", claims));
                when(claims.get("ver", Number.class)).thenReturn(null);
                assertFalse(invokeBoolean("isVersionTwo", claims));
        }

        @Test
        void rejectsBlankVersionTwoSubject() {
                final String token = Jwts.builder()
                                .header().keyId(JWT_KEY_ID).and()
                                .issuer(com.oodesigns.cas.domain.service.TokenService.TOKEN_ISSUER)
                                .subject("  ")
                                .audience().add("2fa_verification").and()
                                .claim("ver", 2)
                                .expiration(Date.from(Instant.now().plusSeconds(300)))
                                .signWith(signingKey, Jwts.SIG.HS256).compact();
                when(keySupplier.getPassword(JWT_KEY_ID)).thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

                assertTrue(verifier.verify2FAVerificationToken(TwoFactorVerificationToken.of(token)).isEmpty());
        }

            @Test
            void coversPrivateClaimValidationCombinations() throws Exception {
                                final String validJti = UUID.randomUUID().toString();
                                final String validSubject = UUID.randomUUID().toString();
                final Claims userClaims = mock(Claims.class);
                lenient().when(userClaims.get("aud")).thenReturn("2fa_verification");
                lenient().when(userClaims.getSubject()).thenReturn(" ");
                assertTrue(invokeOptional("extractVersionTwoUserId", new Class<?>[] {Claims.class, String.class},
                        userClaims, "2fa_verification").isEmpty());
                lenient().when(userClaims.getSubject()).thenReturn(UUID.randomUUID().toString());
                assertTrue(invokeOptional("extractVersionTwoUserId", new Class<?>[] {Claims.class, String.class},
                        userClaims, "2fa_verification").isPresent());

                final Claims accessClaims = mock(Claims.class);
                lenient().when(accessClaims.get("aud")).thenReturn("access_token");
                lenient().when(accessClaims.getSubject()).thenReturn(UUID.randomUUID().toString());
                lenient().when(accessClaims.getId()).thenReturn(UUID.randomUUID().toString());
                lenient().when(accessClaims.getExpiration()).thenReturn(new Date());
                lenient().when(accessTokenRevocationStore.isInvalidated(org.mockito.ArgumentMatchers.any())).thenReturn(false);
                assertTrue(invokeOptional("extractVersionTwoAccessTokenClaims", new Class<?>[] {Claims.class}, accessClaims).isPresent());
                lenient().when(accessClaims.getExpiration()).thenReturn(null);
                assertTrue(invokeOptional("extractVersionTwoAccessTokenClaims", new Class<?>[] {Claims.class}, accessClaims).isEmpty());

                lenient().when(accessClaims.get("aud")).thenReturn("wrong");
                lenient().when(accessClaims.getSubject()).thenReturn(UUID.randomUUID().toString());
                lenient().when(accessClaims.getId()).thenReturn(UUID.randomUUID().toString());
                lenient().when(accessClaims.getExpiration()).thenReturn(new Date());
                assertTrue(invokeOptional("extractVersionTwoAccessTokenClaims", new Class<?>[] {Claims.class}, accessClaims).isEmpty());
                lenient().when(accessClaims.get("aud")).thenReturn("access_token");
                lenient().when(accessClaims.getSubject()).thenReturn(null);
                assertTrue(invokeOptional("extractVersionTwoAccessTokenClaims", new Class<?>[] {Claims.class}, accessClaims).isEmpty());
                lenient().when(accessClaims.getSubject()).thenReturn(" ");
                assertTrue(invokeOptional("extractVersionTwoAccessTokenClaims", new Class<?>[] {Claims.class}, accessClaims).isEmpty());
                lenient().when(accessClaims.getSubject()).thenReturn(UUID.randomUUID().toString());
                lenient().when(accessClaims.getId()).thenReturn(null);
                assertTrue(invokeOptional("extractVersionTwoAccessTokenClaims", new Class<?>[] {Claims.class}, accessClaims).isEmpty());
                lenient().when(accessClaims.getId()).thenReturn(" ");
                assertTrue(invokeOptional("extractVersionTwoAccessTokenClaims", new Class<?>[] {Claims.class}, accessClaims).isEmpty());

                assertTrue(invokeOptional("extractAccessTokenClaims", new Class<?>[] {String.class, Date.class},
                        null, new Date()).isEmpty());
                assertTrue(invokeOptional("extractAccessTokenClaims", new Class<?>[] {String.class, Date.class},
                        "{\"sub\":\"%s\",\"jti\":\"%s\"}".formatted(validSubject, validJti), new Date()).isPresent());
                assertTrue(invokeOptional("extractAccessTokenClaims", new Class<?>[] {String.class, Date.class},
                        "{}", new Date()).isEmpty());
                assertTrue(invokeOptional("extractAccessTokenClaims", new Class<?>[] {String.class, Date.class},
                        "{\"aud\":null}", new Date()).isEmpty());
                assertTrue(invokeOptional("extractAccessTokenClaims", new Class<?>[] {String.class, Date.class},
                        "{\"aud\":\"\"}", new Date()).isEmpty());
                assertTrue(invokeOptional("extractAccessTokenClaims", new Class<?>[] {String.class, Date.class},
                        "{\"aud\":\"access_token\"}", new Date()).isEmpty());
                assertTrue(invokeOptional("extractAccessTokenClaims", new Class<?>[] {String.class, Date.class},
                        "{\"sub\":\"\",\"jti\":\"%s\"}".formatted(validJti), new Date()).isEmpty());
                assertTrue(invokeOptional("extractAccessTokenClaims", new Class<?>[] {String.class, Date.class},
                        "{\"sub\":\"%s\",\"jti\":\"\"}".formatted(validSubject), new Date()).isEmpty());
                assertTrue(invokeOptional("extractAccessTokenClaims", new Class<?>[] {String.class, Date.class},
                        "{\"sub\":\"%s\"}".formatted(validSubject), new Date()).isEmpty());
            }

            @SuppressWarnings("unchecked")
            private Optional<Object> invokeOptional(final String methodName, final Class<?>[] parameterTypes,
                                                    final Object... arguments) throws Exception {
                final var method = JwtTokenVerifier.class.getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return (Optional<Object>) method.invoke(verifier, arguments);
            }

        private boolean invokeBoolean(final String methodName, final Claims claims) throws Exception {
                final var method = JwtTokenVerifier.class.getDeclaredMethod(methodName, Claims.class);
                method.setAccessible(true);
                return (boolean) method.invoke(verifier, claims);
        }

    // -------------------------------------------------------------------------
    // Refresh token verification
    // -------------------------------------------------------------------------

    @Test
    void verifyRefreshToken_ReturnsUserId_WhenTokenIsValid() {
        final UUID userId = UUID.randomUUID();
        final String token = buildValidToken(userId, "refresh_token", Instant.now().plusSeconds(600));

        when(keySupplier.getPassword(JWT_KEY_ID))
                .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

        final Optional<UserId> result = verifier.verifyRefreshToken(RefreshToken.of(token));

        assertTrue(result.isPresent());
        assertEquals(userId, result.get().asUUID());
    }

        @Test
        void returnsEmptyWhenKeyPasswordCannotBeRead() {
                final KeyPassword password = mock(KeyPassword.class);
                when(password.toUtf8Bytes()).thenThrow(new IllegalStateException("key unavailable"));
                when(keySupplier.getPassword(JWT_KEY_ID)).thenReturn(Optional.of(password));

                assertTrue(verifier.verifyAccessToken(AccessToken.of("one.two.three")).isEmpty());
                assertTrue(verifier.verify2FAVerificationToken(TwoFactorVerificationToken.of("one.two.three")).isEmpty());
        }

        @Test
        void handlesParserFailuresFromKeyMaterialDuringPrivateVerification() throws Exception {
                final KeyPassword password = mock(KeyPassword.class);
                lenient().when(password.toUtf8Bytes()).thenThrow(new IllegalStateException("key unavailable"));
                final var parseAccess = JwtTokenVerifier.class.getDeclaredMethod(
                        "parseAndVerifyAccessToken", String.class, KeyPassword.class);
                parseAccess.setAccessible(true);
                final var parsePurpose = JwtTokenVerifier.class.getDeclaredMethod(
                        "parseAndVerify", String.class, KeyPassword.class, String.class);
                parsePurpose.setAccessible(true);

                assertTrue(((Optional<?>) parseAccess.invoke(verifier, "token", password)).isEmpty());
                assertTrue(((Optional<?>) parsePurpose.invoke(verifier, "token", password, "access_token")).isEmpty());
        }

    @Test
    void verifyRefreshToken_ReturnsEmpty_WhenTokenIsNull() {
        assertTrue(verifier.verifyRefreshToken(null).isEmpty());
    }

    @Test
    void verifyRefreshToken_ReturnsEmpty_WhenTokenIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> verifier.verifyRefreshToken(RefreshToken.of("  ")));
    }

    @Test
    void verifyRefreshToken_ReturnsEmpty_WhenTokenIsExpired() {
        final UUID userId = UUID.randomUUID();
        final String token = buildValidToken(userId, "refresh_token", Instant.now().minusSeconds(1));

        when(keySupplier.getPassword(JWT_KEY_ID))
                .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

        assertTrue(verifier.verifyRefreshToken(RefreshToken.of(token)).isEmpty());
    }

    @Test
    void verifyRefreshToken_ReturnsEmpty_WhenAudienceIsWrong() {
        // A 2FA verification token must NOT be usable as a refresh token.
        final UUID userId = UUID.randomUUID();
        final String token = buildValidToken(userId, "2fa_verification", Instant.now().plusSeconds(600));

        when(keySupplier.getPassword(JWT_KEY_ID))
                .thenReturn(Optional.of(KeyPassword.of(TEST_SECRET)));

        assertTrue(verifier.verifyRefreshToken(RefreshToken.of(token)).isEmpty());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildValid2FAToken(final UUID userId, final String audience,
                                      final Instant expiresAt) {
        return buildValidToken(userId, audience, expiresAt);
    }

    private String buildValidToken(final UUID userId, final String audience,
                                   final Instant expiresAt) {
        final String payloadJson = buildPayloadJson(userId.toString(), audience, expiresAt);
        return Jwts.builder()
                .claim("payload", payloadJson)
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private String buildAccessToken(final UUID userId,
                                    final UUID jti,
                                    final Instant expiresAt,
                                    final boolean omitAudience) {
        final String payloadJson = omitAudience
                ? String.format(
                "{\"sub\":\"%s\",\"permissions\":[\"read\"],\"iat\":%d,\"exp\":%d,\"jti\":\"%s\"}",
                userId, Instant.now().getEpochSecond(), expiresAt.getEpochSecond(), jti)
                : String.format(
                "{\"sub\":\"%s\",\"aud\":\"refresh_token\",\"permissions\":[\"read\"],\"iat\":%d,\"exp\":%d,\"jti\":\"%s\"}",
                userId, Instant.now().getEpochSecond(), expiresAt.getEpochSecond(), jti);
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


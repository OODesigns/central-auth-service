package com.oodesigns.cas.integration.grpc;

import com.oodesigns.cas.application.command.DisableTotpCommandHandler;
import com.oodesigns.cas.application.command.EnableTotpCommandHandler;
import com.oodesigns.cas.application.command.LoginCommandHandler;
import com.oodesigns.cas.application.command.LogoutCommandHandler;
import com.oodesigns.cas.application.command.RefreshTokenCommandHandler;
import com.oodesigns.cas.application.command.SetupTotpCommandHandler;
import com.oodesigns.cas.application.command.VerifyTotpCommandHandler;
import com.oodesigns.cas.domain.service.AuthenticationService;
import com.oodesigns.cas.domain.service.TotpCodeGenerator;
import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.value.SecretFor2FA;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.infrastructure.adapter.BcryptPasswordVerifier;
import com.oodesigns.cas.infrastructure.adapter.EnvironmentKeySupplier;
import com.oodesigns.cas.infrastructure.adapter.JooqTotpSetupProvider;
import com.oodesigns.cas.infrastructure.adapter.JooqTotpStatusReader;
import com.oodesigns.cas.infrastructure.adapter.JooqTotpVerifier;
import com.oodesigns.cas.infrastructure.adapter.JooqUserCredentialByIdReader;
import com.oodesigns.cas.infrastructure.adapter.JooqAccessTokenRevocationStore;
import com.oodesigns.cas.infrastructure.adapter.JooqRefreshTokenStore;
import com.oodesigns.cas.infrastructure.adapter.JwtTokenSigner;
import com.oodesigns.cas.infrastructure.adapter.JwtTokenVerifier;
import com.oodesigns.cas.infrastructure.adapter.LoginRateLimiter;
import com.oodesigns.cas.infrastructure.adapter.SystemClock;
import com.oodesigns.cas.infrastructure.adapter.TotpRateLimiter;
import com.oodesigns.cas.infrastructure.adapter.UserCredentialReader;
import com.oodesigns.cas.infrastructure.adapter.UserRepository;
import com.oodesigns.cas.infrastructure.config.DatabaseConfig;
import com.oodesigns.cas.infrastructure.config.DatabaseContextFactory;
import com.oodesigns.cas.infrastructure.grpc.AuthGrpcService;
import com.oodesigns.cas.util.file.FileLoaderProviderFactory;
import com.oodesigns.cas.util.properties.EnvironmentVariableTransformer;
import com.oodesigns.cas.util.properties.PropertiesReader;
import com.oodesigns.cas.infrastructure.grpc.proto.AuthServiceGrpc;
import com.oodesigns.cas.infrastructure.grpc.proto.DisableTotpRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.DisableTotpResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.EnableTotpRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.EnableTotpResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.LoginRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.LoginResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.LogoutRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.LogoutResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.RefreshRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.RefreshResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.SetupTotpRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.SetupTotpResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.VerifyTotpRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.VerifyTotpResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end smoke test for the gRPC delivery layer against the live docker-compose database.
 * <p>
 * This test boots the real gRPC server in-process and exercises the full flow over a
 * blocking gRPC client: login → setup TOTP → enable TOTP → verify TOTP → disable TOTP.
 * The database is the compose-backed PostgreSQL instance seeded by Flyway.
 */
@Tag("database")
@Tag("smoke")
@Tag("integration")
class GrpcSmokeTest {

    private static final String DEFAULT_DB_HOST = "localhost";
    private static final String DEFAULT_JWT_SECRET = "smoke-test-jwt-secret-smoke-test-jwt-secret-0123456789";
    private static final String DEFAULT_TOTP_ENCRYPTION_KEY = "smoke-test-totp-encryption-key-smoke-test-0123456789";
    private static final String DEFAULT_POSTGRES_USER = "postgres";
    private static final String DEFAULT_POSTGRES_PASSWORD = "postgres";
    private static final String TEST_PASSWORD = "SmokePassword123!";
    private static final String TEST_IP = "127.0.0.1";
    private static final String TEST_USERNAME = "grpc_smoke";

    private static volatile boolean defaultsInitialized;

    private DSLContext adminDsl;
    private Connection adminConnection;
    private Server server;
    private ManagedChannel channel;
    private AuthServiceGrpc.AuthServiceBlockingStub stub;
    private UserId createdUserId;
        private JwtTokenVerifier tokenVerifier;
        private String revokedAccessTokenHash;

    @BeforeAll
    static void initializeDefaults() {
        if (defaultsInitialized) {
            return;
        }
        System.setProperty("DB_HOST", System.getProperty("DB_HOST", DEFAULT_DB_HOST));
        System.setProperty("JWT_SECRET", System.getProperty("JWT_SECRET", DEFAULT_JWT_SECRET));
        System.setProperty("TOTP_ENCRYPTION_KEY", System.getProperty("TOTP_ENCRYPTION_KEY", DEFAULT_TOTP_ENCRYPTION_KEY));
        System.setProperty("KEYSTORE_PASSWORD", System.getProperty("KEYSTORE_PASSWORD", DEFAULT_TOTP_ENCRYPTION_KEY));
        System.setProperty("TRUSTSTORE_PASSWORD", System.getProperty("TRUSTSTORE_PASSWORD", DEFAULT_TOTP_ENCRYPTION_KEY));
        System.setProperty("POSTGRES_USER", System.getProperty("POSTGRES_USER", DEFAULT_POSTGRES_USER));
        System.setProperty("POSTGRES_PASSWORD", System.getProperty("POSTGRES_PASSWORD", DEFAULT_POSTGRES_PASSWORD));
        System.setProperty("API_USER", System.getProperty("API_USER", "app_user"));
        System.setProperty("API_PASSWORD", System.getProperty("API_PASSWORD", "DefaultP@ss123"));
        System.setProperty("APP_DB", System.getProperty("APP_DB", "auth_db"));
        defaultsInitialized = true;
    }

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(isDatabaseAvailable(), "Live PostgreSQL database is not available");

        final PropertiesReader propertiesReader = new PropertiesReader(
                "application.properties",
                new EnvironmentVariableTransformer(),
                FileLoaderProviderFactory.defaultProvider()
        );
        final DatabaseConfig databaseConfig = new DatabaseConfig(propertiesReader);
        final DSLContext appDsl = DatabaseContextFactory.create(databaseConfig);

        adminConnection = openAdminConnection(databaseConfig);
        adminDsl = org.jooq.impl.DSL.using(adminConnection, org.jooq.SQLDialect.POSTGRES);

        final EnvironmentKeySupplier keySupplier = new EnvironmentKeySupplier(name -> {
            final String envValue = System.getenv(name);
            if (envValue != null && !envValue.isBlank()) {
                return envValue;
            }
            final String propertyValue = System.getProperty(name);
            return (propertyValue == null || propertyValue.isBlank()) ? null : propertyValue;
        });

        final AuthenticationService authenticationService = new AuthenticationService(new BcryptPasswordVerifier());
        final TokenService tokenService = new TokenService(new SystemClock(), new JwtTokenSigner(keySupplier, "JWT_SECRET"));
        final UserCredentialReader credentialReader = new UserCredentialReader(appDsl);
        final UserRepository userRepository = new UserRepository(appDsl);
        final JooqTotpStatusReader totpStatusReader = new JooqTotpStatusReader(appDsl);
        final JooqTotpVerifier totpVerifier = new JooqTotpVerifier(appDsl, new SystemClock(), keySupplier, "TOTP_ENCRYPTION_KEY");
        final JooqTotpSetupProvider totpSetupProvider = new JooqTotpSetupProvider(appDsl, keySupplier, "TOTP_ENCRYPTION_KEY");
        final JooqUserCredentialByIdReader credentialByIdReader = new JooqUserCredentialByIdReader(appDsl);
        final JooqRefreshTokenStore refreshTokenStore = new JooqRefreshTokenStore(appDsl);
        final JooqAccessTokenRevocationStore accessTokenRevocationStore = new JooqAccessTokenRevocationStore(appDsl);

        final LoginCommandHandler loginHandler = new LoginCommandHandler(
                authenticationService,
                tokenService,
                credentialReader,
                userRepository,
                totpStatusReader,
                new LoginRateLimiter(),
                refreshTokenStore
        );
        final SetupTotpCommandHandler setupTotpHandler = new SetupTotpCommandHandler(totpSetupProvider, "CentralAuthService");
        final EnableTotpCommandHandler enableTotpHandler = new EnableTotpCommandHandler(totpVerifier, totpSetupProvider);
        tokenVerifier = new JwtTokenVerifier(
                keySupplier, "JWT_SECRET", accessTokenRevocationStore);
        final VerifyTotpCommandHandler verifyTotpHandler = new VerifyTotpCommandHandler(
                tokenVerifier,
                totpVerifier,
                userRepository,
                tokenService,
                new TotpRateLimiter(),
                refreshTokenStore
        );
        final DisableTotpCommandHandler disableTotpHandler = new DisableTotpCommandHandler(
                authenticationService,
                credentialByIdReader,
                totpSetupProvider
        );
        final RefreshTokenCommandHandler refreshTokenHandler = new RefreshTokenCommandHandler(
                tokenVerifier,
                userRepository,
                tokenService,
                refreshTokenStore
        );
        final LogoutCommandHandler logoutHandler = new LogoutCommandHandler(tokenVerifier, accessTokenRevocationStore);

        server = NettyServerBuilder.forPort(0)
                .intercept(new com.oodesigns.cas.infrastructure.grpc.GrpcAuthInterceptor(tokenVerifier))
                .addService(new AuthGrpcService(
                        loginHandler,
                        setupTotpHandler,
                        enableTotpHandler,
                        verifyTotpHandler,
                        disableTotpHandler,
                        refreshTokenHandler,
                        logoutHandler))
                .build()
                .start();

        channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
                .usePlaintext()
                .build();
        stub = AuthServiceGrpc.newBlockingStub(channel);

        createdUserId = createSmokeUser();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (channel != null) {
            channel.shutdownNow();
            channel.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow();
            server.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        }
                if (adminDsl != null && createdUserId != null) {
            //noinspection SqlResolve
            adminDsl.execute("DELETE FROM private_schema.users WHERE user_id = ?", createdUserId.value());
        }
                if (adminDsl != null && revokedAccessTokenHash != null) {
                        //noinspection SqlResolve
                        adminDsl.execute("DELETE FROM private_schema.invalidated_jwts WHERE token_hash = ?", revokedAccessTokenHash);
                }
        if (adminConnection != null && !adminConnection.isClosed()) {
            adminConnection.close();
        }
        channel = null;
        server = null;
        stub = null;
        adminDsl = null;
        adminConnection = null;
        createdUserId = null;
    }

    @Test
    void grpcSmokeFlow_CoversLoginSetupEnableVerifyAndDisableAgainstLiveServer() {
        final LoginResponse initialLogin = stub.login(LoginRequest.newBuilder()
                .setUsername(TEST_USERNAME)
                .setPassword(TEST_PASSWORD)
                .setIpAddress(TEST_IP)
                .build());
        assertTrue(initialLogin.hasSuccess(), "Initial login should succeed before TOTP is enabled");
        assertFalse(initialLogin.getSuccess().getPermissionsList().isEmpty(), "Role permissions should be returned");

        final String accessToken = initialLogin.getSuccess().getAccessToken();
        final AuthServiceGrpc.AuthServiceBlockingStub authenticatedStub = authorizedStub(accessToken);
        final SetupTotpResponse setup = authenticatedStub.setupTotp(SetupTotpRequest.newBuilder()
                .setUserId(createdUserId.value().toString())
                .setUsername(TEST_USERNAME)
                .build());
        assertTrue(setup.hasSuccess(), "TOTP setup should return a secret and otpauth URI");
        assertFalse(setup.getSuccess().getSecret().isEmpty(), "TOTP secret should be present");

        final String otp = new TotpCodeGenerator(new SystemClock())
                .generate(SecretFor2FA.of(setup.getSuccess().getSecret()));

        final EnableTotpResponse enable = authenticatedStub.enableTotp(EnableTotpRequest.newBuilder()
                .setUserId(createdUserId.value().toString())
                .setTotpCode(otp)
                .build());
        assertTrue(enable.hasSuccess(), "TOTP enable should succeed with a valid OTP");
        assertFalse(enable.getSuccess().getBackupCodesList().isEmpty(), "Backup codes should be returned");

        final LoginResponse challenge = stub.login(LoginRequest.newBuilder()
                .setUsername(TEST_USERNAME)
                .setPassword(TEST_PASSWORD)
                .setIpAddress(TEST_IP)
                .build());
        assertTrue(challenge.hasTotpRequired(), "Login should now require 2FA verification");

        final VerifyTotpResponse verified = stub.verifyTotp(VerifyTotpRequest.newBuilder()
                .setVerificationToken(challenge.getTotpRequired().getVerificationToken())
                .setCode(otp)
                .build());
        assertTrue(verified.hasSuccess(), "VerifyTotp should exchange the 2FA token for access tokens");
        assertEquals(createdUserId.value().toString(), verified.getSuccess().getUserId());
        assertFalse(verified.getSuccess().getPermissionsList().isEmpty(), "Permissions should be returned");

        // --- Refresh-token rotation ---------------------------------------------------------
        final String issuedRefreshToken = verified.getSuccess().getRefreshToken();
        final RefreshResponse rotated = stub.refresh(RefreshRequest.newBuilder()
                .setRefreshToken(issuedRefreshToken)
                .build());
        assertTrue(rotated.hasSuccess(), "Refresh should rotate the token and issue a fresh pair");
        assertFalse(rotated.getSuccess().getRefreshToken().isEmpty(), "A new refresh token should be issued");
        assertNotEquals(issuedRefreshToken, rotated.getSuccess().getRefreshToken(),
                "Rotation must issue a different refresh token");

        // Replaying the now-consumed token must trigger reuse detection (family revoked).
        final RefreshResponse reuse = stub.refresh(RefreshRequest.newBuilder()
                .setRefreshToken(issuedRefreshToken)
                .build());
        assertTrue(reuse.hasError(), "Replaying a rotated refresh token must fail");
        assertEquals("REFRESH_TOKEN_REUSE_DETECTED", reuse.getError().getErrorCode(),
                "Reuse of a rotated token must be detected");

        // The replacement issued by the reuse-detected family is now revoked too.
        final RefreshResponse afterReuse = stub.refresh(RefreshRequest.newBuilder()
                .setRefreshToken(rotated.getSuccess().getRefreshToken())
                .build());
        assertTrue(afterReuse.hasError(), "The whole family must be revoked after reuse detection");

        final String verifiedAccessToken = verified.getSuccess().getAccessToken();
        final AuthServiceGrpc.AuthServiceBlockingStub verifiedStub = authorizedStub(verifiedAccessToken);
        final DisableTotpResponse disabled = verifiedStub.disableTotp(DisableTotpRequest.newBuilder()
                .setUserId(createdUserId.value().toString())
                .setPassword(TEST_PASSWORD)
                .setReason(com.oodesigns.cas.infrastructure.grpc.proto.DisableReason.USER_REQUESTED)
                .build());
        assertTrue(disabled.hasSuccess(), "DisableTotp should succeed after password re-authentication");

        final LogoutResponse logout = verifiedStub.logout(LogoutRequest.newBuilder()
                .setAccessToken(verifiedAccessToken)
                .build());
        assertTrue(logout.hasSuccess(), "Logout should revoke the presented access token");
        revokedAccessTokenHash = sha256Hex(verifiedAccessToken);
        assertEquals(1L, countInvalidatedTokens(revokedAccessTokenHash), "Logout should persist a revocation row");
        assertTrue(tokenVerifier.verifyAccessToken(verifiedAccessToken).isEmpty(),
                "The same verifier must reject the revoked access token");

        final LoginResponse afterDisable = stub.login(LoginRequest.newBuilder()
                .setUsername(TEST_USERNAME)
                .setPassword(TEST_PASSWORD)
                .setIpAddress(TEST_IP)
                .build());
        assertTrue(afterDisable.hasSuccess(), "Login should succeed again once TOTP is disabled");
    }

        private AuthServiceGrpc.AuthServiceBlockingStub authorizedStub(final String accessToken) {
                final Metadata headers = new Metadata();
                headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
                                "Bearer " + accessToken);
                return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
        }

    private UserId createSmokeUser() {
        final UUID userId = UUID.randomUUID();
        final UUID roleId = adminRoleId();
        final String passwordHash = new BCryptPasswordEncoder().encode(TEST_PASSWORD);

        //noinspection SqlResolve
        adminDsl.execute(
                "INSERT INTO private_schema.users (user_id, username, password_hash, role_id, password_reset_required_at, mfa_required_at) VALUES (?, ?, ?, ?, NULL, NULL)",
                userId,
                TEST_USERNAME,
                passwordHash,
                roleId
        );

        return UserId.of(userId);
    }

    private UUID adminRoleId() {
        //noinspection SqlResolve
        final Record record = adminDsl.fetchOne(
                "SELECT role_id FROM private_schema.roles WHERE name = ?",
                "admin"
        );
        assertNotNull(record, "Admin role should exist in seeded data");
        return record.get("role_id", UUID.class);
    }

    private Connection openAdminConnection(final DatabaseConfig databaseConfig) throws SQLException {
        final String adminUser = System.getProperty("POSTGRES_USER", DEFAULT_POSTGRES_USER);
        final String adminPassword = System.getProperty("POSTGRES_PASSWORD", DEFAULT_POSTGRES_PASSWORD);
        final String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s",
                databaseConfig.getHost(),
                databaseConfig.getPort(),
                databaseConfig.getDatabaseName());
        return DriverManager.getConnection(jdbcUrl, adminUser, adminPassword);
    }

    private boolean isDatabaseAvailable() {
        try {
            final PropertiesReader propertiesReader = new PropertiesReader(
                    "application.properties",
                    new EnvironmentVariableTransformer(),
                    FileLoaderProviderFactory.defaultProvider()
            );
            final DatabaseConfig config = new DatabaseConfig(propertiesReader);
            DatabaseContextFactory.create(config);
            return true;
        } catch (final RuntimeException e) {
            return false;
        }
    }

        private long countInvalidatedTokens(final String tokenHash) {
                //noinspection SqlResolve
                final Record record = adminDsl.fetchOne(
                                "SELECT count(*) AS count FROM private_schema.invalidated_jwts WHERE token_hash = ?",
                                tokenHash);
                assertNotNull(record, "Expected a row when counting invalidated tokens");
                return record.get("count", Long.class);
        }

        private static String sha256Hex(final String token) {
                try {
                        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
                } catch (final Exception e) {
                        throw new IllegalStateException(e);
                }
        }
}

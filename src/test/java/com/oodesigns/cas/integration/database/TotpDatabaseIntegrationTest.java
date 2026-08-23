package com.oodesigns.cas.integration.database;

import com.oodesigns.cas.application.command.DisableReason;
import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.service.TotpCodeGenerator;
import com.oodesigns.cas.domain.value.BackupCode;
import com.oodesigns.cas.domain.value.KeyPassword;
import com.oodesigns.cas.domain.value.SecretFor2FA;
import com.oodesigns.cas.domain.value.TotpCode;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.infrastructure.adapter.JooqTotpSetupProvider;
import com.oodesigns.cas.infrastructure.adapter.JooqTotpStatusReader;
import com.oodesigns.cas.infrastructure.adapter.JooqTotpVerifier;
import com.oodesigns.cas.infrastructure.adapter.KeySupplier;
import com.oodesigns.cas.infrastructure.config.DatabaseConfig;
import com.oodesigns.cas.infrastructure.config.DatabaseContextFactory;
import com.oodesigns.cas.util.file.FileLoaderProviderFactory;
import com.oodesigns.cas.util.properties.EnvironmentVariableTransformer;
import com.oodesigns.cas.util.properties.PropertiesReader;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Database integration tests for the TOTP migration and adapters.
 * <p>
 * Uses the real PostgreSQL database and the hand-written JOOQ adapters so the 2FA
 * setup, verification, and status flow are exercised end to end.
 */
@SuppressWarnings({"SqlResolve", "FieldCanBeLocal"})
@Tag("database")
@Tag("integration")
class TotpDatabaseIntegrationTest {

    private static final String TEST_TOTP_KEY = "0123456789ABCDEF0123456789ABCDEF";
    private static final Instant FIXED_NOW = Instant.parse("2026-08-10T12:00:00Z");

    private DatabaseConfig databaseConfig;
    private DSLContext dslContext;
    private DSLContext adminDsl;
    private UserId createdUserId;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(isDatabaseAvailable(), "Live PostgreSQL database is not available");

        final PropertiesReader propertiesReader = new PropertiesReader(
            "application.properties",
            new EnvironmentVariableTransformer(),
            FileLoaderProviderFactory.defaultProvider()
        );
        databaseConfig = new DatabaseConfig(propertiesReader);
        dslContext = DatabaseContextFactory.create(databaseConfig);
        adminDsl = createAdminDsl();
    }

    /**
     * Privileged connection for test fixtures and row-level verification.
     * The application connection uses the restricted API role, which (by design)
     * cannot read or write private_schema tables directly.
     */
    private DSLContext createAdminDsl() {
        final String adminUser = System.getenv().getOrDefault("POSTGRES_USER", "postgres");
        final String adminPassword = System.getenv().getOrDefault("POSTGRES_PASSWORD", "postgres");
        final String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s",
            databaseConfig.getHost(), databaseConfig.getPort(), databaseConfig.getDatabaseName());
        try {
            final java.sql.Connection connection =
                java.sql.DriverManager.getConnection(jdbcUrl, adminUser, adminPassword);
            return org.jooq.impl.DSL.using(connection, org.jooq.SQLDialect.POSTGRES);
        } catch (final java.sql.SQLException e) {
            throw new IllegalStateException("Could not open admin fixture connection", e);
        }
    }

    @AfterEach
    void tearDown() {
        if (createdUserId != null) {
            adminDsl.execute("DELETE FROM private_schema.users WHERE user_id = ?", createdUserId.value());
        }
        createdUserId = null;
    }

    @Test
    void migrationCreatesTotpTablesAndFunctions() {
        assertTrue(tableExists("totp_secrets"));
        assertTrue(tableExists("backup_codes"));

        assertTrue(functionExists("get_totp_status"));
        assertTrue(functionExists("get_totp_secret"));
        assertTrue(functionExists("get_pending_totp_secret"));
        assertTrue(functionExists("find_unused_backup_code_hashes"));
        assertTrue(functionExists("mark_totp_last_used"));
        assertTrue(functionExists("store_totp_secret"));
        assertTrue(functionExists("enable_totp"));
        assertTrue(functionExists("disable_totp"));
        assertTrue(functionExists("insert_backup_codes"));
        assertTrue(functionExists("consume_backup_code"));
        assertTrue(functionExists("consume_totp_counter"));
    }

    @Test
    void setupEnableVerifyAndDisableRoundTripsAgainstDatabase() {
        final UserId userId = createTemporaryUser();
        final Ports.Clock clock = () -> FIXED_NOW;
        final KeySupplier keySupplier = ignoredKeyId -> Optional.of(KeyPassword.of(TEST_TOTP_KEY));

        final JooqTotpSetupProvider setupProvider = new JooqTotpSetupProvider(dslContext, keySupplier, "TOTP_ENCRYPTION_KEY");
        final JooqTotpStatusReader statusReader = new JooqTotpStatusReader(dslContext);
        final JooqTotpVerifier verifier = new JooqTotpVerifier(dslContext, clock, keySupplier, "TOTP_ENCRYPTION_KEY");
        final TotpCodeGenerator codeGenerator = new TotpCodeGenerator(clock);

        final String secret = setupProvider.generateSecret(userId);
        assertNotNull(secret);
        assertEquals(1, totpSecretRowCount(userId));
        assertTrue(statusReader.check2FAStatus(userId).isEmpty());
        assertFalse(verifier.isTotpEnabled(userId));

        final String otp = codeGenerator.generate(SecretFor2FA.of(secret));
        // Login-time verification must NOT accept a code against a pending (not-yet-enabled) secret,
        // but the enrolment path (verifySetupCode) must accept it against the pending secret.
        assertFalse(verifier.verifyCode(userId, TotpCode.of(otp)));
        assertTrue(verifier.verifySetupCode(userId, TotpCode.of(otp)));

        assertTrue(setupProvider.enableTotp(userId));
        assertTrue(statusReader.check2FAStatus(userId).isPresent());
        assertTrue(verifier.isTotpEnabled(userId));
        assertTrue(verifier.verifyCode(userId, TotpCode.of(otp)));
        assertFalse(verifier.verifyCode(userId, TotpCode.of(otp)),
            "The same TOTP counter must not be accepted twice");

        final List<BackupCode> backupCodes = setupProvider.generateBackupCodes(userId);
        assertEquals(backupCodeGeneratorDefaultCount(), backupCodes.size());
        assertEquals(backupCodes.size(), backupCodeRowCount(userId));

        final BackupCode firstBackupCode = backupCodes.getFirst();
        assertTrue(verifier.verifyBackupCode(userId, firstBackupCode));
        assertFalse(verifier.verifyBackupCode(userId, firstBackupCode));
        assertEquals(1, usedBackupCodeRowCount(userId));
        assertTrue(lastUsedAtIsPopulated(userId));

        assertTrue(setupProvider.disableTotp(userId, DisableReason.USER_REQUESTED));
        assertTrue(statusReader.check2FAStatus(userId).isEmpty());
        assertFalse(verifier.isTotpEnabled(userId));
        assertEquals(0, totpSecretRowCount(userId));
        assertEquals(0, backupCodeRowCount(userId));
    }

    @Test
    void setupProviderCanReEnrollAfterDisable() {
        final UserId userId = createTemporaryUser();
        final KeySupplier keySupplier = ignoredKeyId -> Optional.of(KeyPassword.of(TEST_TOTP_KEY));
        final JooqTotpSetupProvider setupProvider = new JooqTotpSetupProvider(dslContext, keySupplier, "TOTP_ENCRYPTION_KEY");
        final JooqTotpStatusReader statusReader = new JooqTotpStatusReader(dslContext);

        final String firstSecret = setupProvider.generateSecret(userId);
        assertNotNull(firstSecret);
        assertTrue(setupProvider.enableTotp(userId));
        assertTrue(statusReader.check2FAStatus(userId).isPresent());

        assertTrue(setupProvider.disableTotp(userId, DisableReason.USER_REQUESTED));
        assertTrue(statusReader.check2FAStatus(userId).isEmpty());

        final String secondSecret = setupProvider.generateSecret(userId);
        assertNotNull(secondSecret);
        assertNotEquals(firstSecret, secondSecret);
        assertEquals(1, totpSecretRowCount(userId));
        assertTrue(statusReader.check2FAStatus(userId).isEmpty());
    }

    private UserId createTemporaryUser() {
        final UUID userId = UUID.randomUUID();
        final String username = "totp_db_" + userId.toString().substring(0, 8);
        final UUID adminRoleId = adminRoleId();
        final String passwordHash = new BCryptPasswordEncoder().encode("TemporaryPassword123!");

        adminDsl.execute(
            "INSERT INTO private_schema.users (user_id, username, password_hash, role_id) VALUES (?, ?, ?, ?)",
            userId,
            username,
            passwordHash,
            adminRoleId
        );

        createdUserId = UserId.of(userId);
        return createdUserId;
    }

    private UUID adminRoleId() {
        final Record record = adminDsl.fetchOne(
            "SELECT role_id FROM private_schema.roles WHERE name = ?",
            "admin"
        );
        assertNotNull(record, "Admin role should exist in seeded data");
        return record.get("role_id", UUID.class);
    }

    private boolean tableExists(final String tableName) {
        // pg_catalog is privilege-independent; information_schema hides tables the
        // restricted API role has no privileges on (by design).
        final Record record = dslContext.fetchOne(
            "SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_tables WHERE schemaname = 'private_schema' AND tablename = ?)",
            tableName
        );
        return record != null && Boolean.TRUE.equals(record.get(0, Boolean.class));
    }

    private boolean functionExists(final String functionName) {
        final Record record = dslContext.fetchOne(
            "SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON p.pronamespace = n.oid WHERE n.nspname = 'api_schema' AND p.proname = ?)",
            functionName
        );
        return record != null && Boolean.TRUE.equals(record.get(0, Boolean.class));
    }

    private int totpSecretRowCount(final UserId userId) {
        return rowCount("SELECT COUNT(*) FROM private_schema.totp_secrets WHERE user_id = ?", userId.value());
    }

    private int backupCodeRowCount(final UserId userId) {
        return rowCount("SELECT COUNT(*) FROM private_schema.backup_codes WHERE user_id = ?", userId.value());
    }

    private int usedBackupCodeRowCount(final UserId userId) {
        return rowCount("SELECT COUNT(*) FROM private_schema.backup_codes WHERE user_id = ? AND used_at IS NOT NULL", userId.value());
    }

    private boolean lastUsedAtIsPopulated(final UserId userId) {
        final Record record = adminDsl.fetchOne(
            "SELECT last_used_at FROM private_schema.totp_secrets WHERE user_id = ?",
            userId.value()
        );
        return record != null && record.get("last_used_at") != null;
    }

    private int rowCount(final String sql, final Object bindValue) {
        final Record record = adminDsl.fetchOne(sql, bindValue);
        assertNotNull(record, "Query should return a row");
        final Number count = record.get(0, Number.class);
        return count == null ? 0 : count.intValue();
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

    private int backupCodeGeneratorDefaultCount() {
        return 10;
    }
}


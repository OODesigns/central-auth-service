package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.UserCredential;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseAdaptersIntegrationTest {

    @Test
    @Tag("database")
    void jooqUserCredentialByIdReader_WorksAgainstRealDatabase() throws Exception {
        // Skip unless explicitly enabled to avoid requiring Docker for all local runs
        org.junit.jupiter.api.Assumptions.assumeTrue(
                "true".equalsIgnoreCase(System.getenv("RUN_DATABASE_TESTS")) || System.getenv("CI") != null,
                "Skipping DB-backed test: set RUN_DATABASE_TESTS=true to enable"
        );

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")) {

            postgres.start();

            final String jdbcUrl = postgres.getJdbcUrl();
            try (Connection conn = DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword())) {
                try (Statement s = conn.createStatement()) {
                    // Minimal schema and function implementing find_user_credentials_by_id
                    s.execute("CREATE SCHEMA IF NOT EXISTS api_schema");
                    s.execute("CREATE TABLE IF NOT EXISTS api_schema.users (user_id uuid PRIMARY KEY, password_hash text)");

                    // Insert a user
                    final UUID id = UUID.randomUUID();
                    final String hash = "$2a$10$12345678901234567890123456789012345678901234567890123";
                    s.execute(String.format("INSERT INTO api_schema.users (user_id, password_hash) VALUES ('%s', '%s')", id, hash));

                    // Create the function used by the adapter
                    s.execute("CREATE OR REPLACE FUNCTION api_schema.find_user_credentials_by_id(p_user_id uuid) RETURNS TABLE (user_id uuid, password_hash text) AS $$\n" +
                            "BEGIN\n" +
                            "    RETURN QUERY SELECT u.user_id, u.password_hash FROM api_schema.users u WHERE u.user_id = p_user_id;\n" +
                            "END;\n" +
                            "$$ LANGUAGE plpgsql VOLATILE;");
                }

                final DSLContext dsl = DSL.using(conn, SQLDialect.POSTGRES);
                final JooqUserCredentialByIdReader reader = new JooqUserCredentialByIdReader(dsl);
                final UUID rawId = dsl.fetchOne("SELECT user_id FROM api_schema.users LIMIT 1").getValue(0, UUID.class);

                final Optional<UserCredential> cred = reader.findCredentialsByUserId(UserId.of(rawId));
                assertTrue(cred.isPresent());
                assertEquals(rawId, cred.get().userId().value());
                assertEquals("$2a$10$12345678901234567890123456789012345678901234567890123", cred.get().passwordHash().value());
            }
        }
    }
}


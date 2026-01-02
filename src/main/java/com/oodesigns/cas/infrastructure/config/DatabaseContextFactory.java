package com.oodesigns.cas.infrastructure.config;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Factory for creating JOOQ DSLContext from DatabaseConfig.
 * Intended to be used as a Spring @Bean for singleton management.
 * 
 * Usage with Spring:
 * @Configuration
 * public class DatabaseConfiguration {
 *     @Bean
 *     public DSLContext dslContext(DatabaseConfig config) {
 *         return DatabaseContextFactory.create(config);
 *     }
 * }
 */
public final class DatabaseContextFactory {
    
    private static final int CONNECTION_TIMEOUT_SECONDS = 30;
    private static final int VALIDATION_TIMEOUT_SECONDS = 5;
    
    private DatabaseContextFactory() {
        // Utility class
    }
    
    /**
     * Create DSLContext from DatabaseConfig.
     * Validates connection at creation time (fail fast).
     * 
     * @param config DatabaseConfig with connection parameters
     * @return DSLContext ready for database operations
     * @throws DatabaseConnectionException if connection validation fails
     */
    public static DSLContext create(final DatabaseConfig config) {
        Objects.requireNonNull(config, "DatabaseConfig cannot be null");
        
        final DataSource dataSource = createAndConfigureDataSource(config);
        validateConnection(dataSource);
        
        return DSL.using(dataSource, SQLDialect.POSTGRES);
    }
    
    /**
     * Create and configure PostgreSQL DataSource from config.
     */
    private static DataSource createAndConfigureDataSource(final DatabaseConfig config) {
        final PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setServerNames(new String[]{config.getHost()});
        dataSource.setPortNumbers(new int[]{config.getPort()});
        dataSource.setDatabaseName(config.getDatabaseName());
        dataSource.setUser(config.getUsername());
        dataSource.setPassword(config.getPassword());
        dataSource.setConnectTimeout(CONNECTION_TIMEOUT_SECONDS);
        dataSource.setLoginTimeout(CONNECTION_TIMEOUT_SECONDS);
        return dataSource;
    }
    
    /**
     * Validate database connection is available.
     * Fails fast on connection errors.
     */
    private static void validateConnection(final DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                throw new DatabaseConnectionException("Database connection validation failed");
            }
        } catch (final SQLException e) {
            throw new DatabaseConnectionException("Unable to connect to database", e);
        }
    }
}

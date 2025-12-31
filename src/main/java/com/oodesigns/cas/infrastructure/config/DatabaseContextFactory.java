package com.oodesigns.cas.infrastructure.config;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Factory for creating JOOQ DSLContext from DatabaseConfig.
 * - Immutable state after construction
 * - Pure functions with single responsibility
 * - Optional-based error handling where appropriate
 * - Clear separation of concerns
 */
public final class DatabaseContextFactory implements AutoCloseable {
    
    private static final Logger LOGGER = Logger.getLogger(DatabaseContextFactory.class.getName());
    private static final int CONNECTION_TIMEOUT_SECONDS = 30;
    private static final int VALIDATION_TIMEOUT_SECONDS = 5;
    
    private final DatabaseConfig config;
    private final Supplier<DSLContext> dslContextSupplier;
    
    public DatabaseContextFactory(final DatabaseConfig config) {
        this.config = Objects.requireNonNull(config, "DatabaseConfig cannot be null");
        this.dslContextSupplier = new MemoizedSupplier<>(this::createDslContext);
    }
    
    /**
     * Get DSLContext for database operations.
     * Thread-safe lazy initialization with memoization.
     *
     * @return DSLContext instance
     * @throws DatabaseConnectionException if connection fails
     */
    public DSLContext getDslContext() {
        return dslContextSupplier.get();
    }
    
    /**
     * Create DSLContext using functional pipeline:
     * config -> dataSource -> validate -> DSLContext
     */
    private DSLContext createDslContext() {
        return buildDataSource()
            .flatMap(this::validateConnection)
            .map(this::createContext)
            .orElseThrow(() -> new DatabaseConnectionException(
                "Failed to create database context"));
    }
    
    /**
     * Build PostgreSQL DataSource from configuration.
     * Returns Optional to handle potential configuration errors gracefully.
     */
    private Optional<DataSource> buildDataSource() {
        try {
            return Optional.of(createConfiguredDataSource());
        } catch (final RuntimeException e) {
            logSevere("Failed to build data source", e);
            return Optional.empty();
        }
    }
    
    /**
     * Create and configure PostgreSQL DataSource.
     * Pure function - all inputs from config, returns new DataSource.
     */
    private DataSource createConfiguredDataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        configureDataSource(dataSource);
        return dataSource;
    }
    
    /**
     * Configure DataSource with connection parameters from config.
     */
    private void configureDataSource(final PGSimpleDataSource dataSource) {
        dataSource.setServerNames(new String[]{config.getHost()});
        dataSource.setPortNumbers(new int[]{config.getPort()});
        dataSource.setDatabaseName(config.getDatabaseName());
        dataSource.setUser(config.getUsername());
        dataSource.setPassword(config.getPassword());
        dataSource.setConnectTimeout(CONNECTION_TIMEOUT_SECONDS);
        dataSource.setLoginTimeout(CONNECTION_TIMEOUT_SECONDS);
    }
    
    /**
     * Validate database connection is available.
     * Returns Optional<DataSource> to chain in pipeline.
     */
    private Optional<DataSource> validateConnection(final DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            return isConnectionValid(conn)
                ? successfulValidation(dataSource)
                : failedValidation();
        } catch (final SQLException e) {
            logSevere("Connection validation error", e);
            return Optional.empty();
        }
    }
    
    /**
     * Check if connection is valid within timeout.
     */
    private boolean isConnectionValid(final Connection conn) throws SQLException {
        return conn.isValid(VALIDATION_TIMEOUT_SECONDS);
    }
    
    /**
     * Handle successful connection validation.
     */
    private Optional<DataSource> successfulValidation(final DataSource dataSource) {
        logInfo("Database connection validated successfully");
        return Optional.of(dataSource);
    }
    
    /**
     * Handle failed connection validation.
     */
    private Optional<DataSource> failedValidation() {
        logSevere("Database connection validation failed - connection not valid");
        return Optional.empty();
    }
    
    /**
     * Create DSLContext from validated DataSource.
     */
    private DSLContext createContext(final DataSource dataSource) {
        return DSL.using(dataSource, SQLDialect.POSTGRES);
    }
    
    @Override
    public void close() {
        logInfo("DatabaseContextFactory closed");
    }
    
    private void logInfo(final String message) {
        LOGGER.log(Level.INFO, message);
    }
    
    private void logSevere(final String message) {
        LOGGER.log(Level.SEVERE, message);
    }
    
    private void logSevere(final String message, final Throwable throwable) {
        LOGGER.log(Level.SEVERE, message, throwable);
    }
    
    /**
     * Thread-safe lazy initialization with memoization.
     * Uses AtomicReference for thread-safe caching without volatile.
     * 
     * Double-checked locking pattern ensures:
     * - Single initialization even under concurrent access
     * - No performance penalty after first initialization
     * - Memory safety through AtomicReference
     */
    private static final class MemoizedSupplier<T> implements Supplier<T> {
        private final Supplier<T> delegate;
        private final AtomicReference<T> cached = new AtomicReference<>();
        
        MemoizedSupplier(final Supplier<T> delegate) {
            this.delegate = Objects.requireNonNull(delegate, "Delegate supplier cannot be null");
        }
        
        @Override
        public T get() {
            T value = cached.get();
            if (value == null) {
                synchronized (this) {
                    value = cached.get();
                    if (value == null) {
                        value = delegate.get();
                        cached.set(value);
                    }
                }
            }
            return value;
        }
    }
}

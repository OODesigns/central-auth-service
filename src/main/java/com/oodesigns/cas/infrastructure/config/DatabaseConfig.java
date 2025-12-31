package com.oodesigns.cas.infrastructure.config;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Declarative database configuration that loads properties and creates JOOQ DSLContext.
 * <p>
 * Follows functional programming principles:
 * - Each method does one thing
 * - Immutable configuration after construction
 * - Pure functions with clear inputs/outputs
 * - Lazy initialization with thread-safe caching
 * </p>
 */
public final class DatabaseConfig implements AutoCloseable {
    
    private static final Logger LOGGER = Logger.getLogger(DatabaseConfig.class.getName());
    private static final String PROPERTIES_FILE = "application.properties";
    private static final int DEFAULT_PORT = 5432;
    private static final int CONNECTION_TIMEOUT_SECONDS = 30;
    private static final int VALIDATION_TIMEOUT_SECONDS = 5;
    
    private final Properties properties;
    private final DatabaseConnectionConfig connectionConfig;
    private final Supplier<DSLContext> dslContextSupplier;
    
    public DatabaseConfig() {
        this.properties = loadAndResolveProperties();
        this.connectionConfig = extractAndValidateConnectionConfig();
        this.dslContextSupplier = createMemoizedDslContextSupplier();
    }
    
    /**
     * Get DSLContext for database operations.
     * Thread-safe lazy initialization with memoization.
     */
    public DSLContext dslContext() {
        return dslContextSupplier.get();
    }
    
    /**
     * Get property value by key.
     */
    public String property(final String key) {
        return properties.getProperty(key);
    }
    
    /**
     * Get property value with fallback default.
     */
    public String property(final String key, final String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    /**
     * Load properties from classpath and resolve environment variables.
     */
    private Properties loadAndResolveProperties() {
        return readPropertiesFromClasspath()
            .map(this::resolveAllProperties)
            .orElseThrow(() -> new DatabaseConfigurationException(
                "Failed to load " + PROPERTIES_FILE));
    }
    
    /**
     * Read properties file from classpath.
     */
    private Optional<Properties> readPropertiesFromClasspath() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (input == null) {
                logSevere("Unable to find " + PROPERTIES_FILE + " in classpath");
                return Optional.empty();
            }
            
            Properties props = new Properties();
            props.load(input);
            logInfo(() -> String.format("Loaded %d properties from %s", props.size(), PROPERTIES_FILE));
            return Optional.of(props);
            
        } catch (IOException e) {
            logSevere("Failed to load " + PROPERTIES_FILE, e);
            return Optional.empty();
        }
    }
    
    /**
     * Resolve all property values with environment variable substitution.
     */
    private Properties resolveAllProperties(final Properties props) {
        props.replaceAll((key, value) -> resolvePropertyValue(value.toString()));
        return props;
    }
    
    /**
     * Resolve single property value replacing ${ENV_VAR:default} patterns.
     * Resolution order: environment variable → system property → default → empty string
     */
    private String resolvePropertyValue(final String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        
        String result = value;
        while (result.contains("${")) {
            int start = result.indexOf("${");
            int end = result.indexOf("}", start);
            
            if (end == -1) {
                logWarning(() -> "Unclosed placeholder in property: " + value);
                break;
            }
            
            String resolved = resolvePlaceholder(result.substring(start + 2, end));
            result = result.substring(0, start) + resolved + result.substring(end + 1);
        }
        
        return result;
    }
    
    /**
     * Resolve single placeholder (ENV_VAR or ENV_VAR:default).
     */
    private String resolvePlaceholder(final String placeholder) {
        String[] parts = placeholder.split(":", 2);
        String envVar = parts[0].trim();
        String defaultVal = parts.length > 1 ? parts[1] : "";
        
        return Optional.ofNullable(System.getenv(envVar))
            .or(() -> Optional.ofNullable(System.getProperty(envVar)))
            .orElse(defaultVal);
    }
    
    /**
     * Extract and validate database connection configuration from properties.
     * Called during construction to fail fast if configuration is invalid.
     */
    private DatabaseConnectionConfig extractAndValidateConnectionConfig() {
        return new DatabaseConnectionConfig(
            propertyOrDefault("db.host", "db"),
            intPropertyOrDefault("db.port", DEFAULT_PORT),
            propertyOrDefault("db.name", "auth_db"),
            propertyOrDefault("db.user", "app_user"),
            propertyOrDefault("db.password", "password")
        );
    }
    
    /**
     * Create memoized supplier for thread-safe lazy DSLContext initialization.
     * Configuration is already validated, so this won't throw configuration errors.
     */
    private Supplier<DSLContext> createMemoizedDslContextSupplier() {
        return new MemoizedSupplier<>(() -> {
            DataSource ds = buildDataSource();
            logInfo(() -> "DSLContext created successfully");
            return DSL.using(ds, SQLDialect.POSTGRES);
        });
    }
    
    /**
     * Build and configure PostgreSQL DataSource from validated configuration.
     */
    private DataSource buildDataSource() {
        PGSimpleDataSource ds = configureDataSource(connectionConfig);
        validateConnection(ds);
        logDataSourceConfiguration(connectionConfig);
        return ds;
    }
    
    /**
     * Configure PostgreSQL DataSource with connection parameters.
     * Configuration is already validated, so this should not fail.
     */
    private PGSimpleDataSource configureDataSource(final DatabaseConnectionConfig config) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setServerNames(new String[]{config.host()});
        ds.setPortNumbers(new int[]{config.port()});
        ds.setDatabaseName(config.database());
        ds.setUser(config.user());
        ds.setPassword(config.password());
        ds.setConnectTimeout(CONNECTION_TIMEOUT_SECONDS);
        ds.setLoginTimeout(CONNECTION_TIMEOUT_SECONDS);
        return ds;
    }
    
    /**
     * Validate database connection.
     * This is called lazily on first DSLContext access, not during construction.
     */
    private void validateConnection(final DataSource ds) {
        try (Connection conn = ds.getConnection()) {
            if (!conn.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                throw new DatabaseConnectionException("Database connection validation failed");
            }
            logInfo(() -> "Database connection test successful");
        } catch (SQLException e) {
            throw new DatabaseConnectionException("Failed to validate database connection", e);
        }
    }
    
    /**
     * Get property with validation and default fallback.
     */
    private String propertyOrDefault(final String key, final String defaultValue) {
        String value = properties.getProperty(key, defaultValue);
        if (value == null || value.trim().isEmpty()) {
            logWarning(() -> String.format("Property '%s' is empty, using default: %s", key, defaultValue));
            return defaultValue;
        }
        return value.trim();
    }
    
    /**
     * Get integer property with parsing and default fallback.
     */
    private int intPropertyOrDefault(final String key, final int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException _) {
            logWarning(() -> String.format(
                "Invalid integer for '%s': %s. Using default: %d", key, value, defaultValue));
            return defaultValue;
        }
    }
    
    /**
     * Log DataSource configuration.
     */
    private void logDataSourceConfiguration(final DatabaseConnectionConfig config) {
        logInfo(() -> String.format("DataSource configured: %s:%d/%s (user: %s)",
            config.host(), config.port(), config.database(), config.user()));
    }
    
    /**
     * Conditional INFO logging.
     */
    private void logInfo(final Supplier<String> messageSupplier) {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.info(messageSupplier.get());
        }
    }
    
    /**
     * Conditional WARNING logging.
     */
    private void logWarning(final Supplier<String> messageSupplier) {
        if (LOGGER.isLoggable(Level.WARNING)) {
            LOGGER.warning(messageSupplier.get());
        }
    }
    
    /**
     * SEVERE logging with message.
     */
    private void logSevere(final String message) {
        LOGGER.severe(message);
    }
    
    /**
     * SEVERE logging with exception.
     */
    private void logSevere(final String message, final Exception e) {
        LOGGER.log(Level.SEVERE, message, e);
    }
    
    @Override
    public void close() {
        // DSLContext is stateless - no cleanup needed
        // DataSource connections are managed by JOOQ
    }
    
    /**
     * Immutable database connection configuration.
     */
    private record DatabaseConnectionConfig(
        String host,
        int port,
        String database,
        String user,
        String password
    ) {
        private DatabaseConnectionConfig {
            Objects.requireNonNull(host, "Host cannot be null");
            Objects.requireNonNull(database, "Database cannot be null");
            Objects.requireNonNull(user, "User cannot be null");
            Objects.requireNonNull(password, "Password cannot be null");
            
            if (host.trim().isEmpty()) {
                throw new IllegalArgumentException("Host cannot be empty");
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535: " + port);
            }
            if (database.trim().isEmpty()) {
                throw new IllegalArgumentException("Database cannot be empty");
            }
            if (user.trim().isEmpty()) {
                throw new IllegalArgumentException("User cannot be empty");
            }
        }
    }
    
    /**
     * Thread-safe memoized supplier for lazy initialization using AtomicReference.
     * Ensures the delegate supplier is called at most once, even under concurrent access.
     */
    private static final class MemoizedSupplier<T> implements Supplier<T> {
        private final Supplier<T> delegate;
        private final java.util.concurrent.atomic.AtomicReference<T> value = 
            new java.util.concurrent.atomic.AtomicReference<>();
        
        private MemoizedSupplier(final Supplier<T> delegate) {
            this.delegate = Objects.requireNonNull(delegate);
        }
        
        @Override
        public T get() {
            T result = value.get();
            if (result == null) {
                synchronized (this) {
                    result = value.get();
                    if (result == null) {
                        result = delegate.get();
                        value.set(result);
                    }
                }
            }
            return result;
        }
    }
}

package com.oodesigns.cas.infrastructure.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.function.Supplier;

/**
 * Database configuration that loads and validates properties.
 * <p>
 * Single Responsibility: Load properties, resolve placeholders, validate against defined schema.
 * Does NOT create DSLContext - that's delegated to DatabaseContextFactory for Spring IoC.
 * </p>
 */
public final class DatabaseConfig {
    
    private static final Logger LOGGER = Logger.getLogger(DatabaseConfig.class.getName());
    private static final String PROPERTIES_FILE = "application.properties";
    
    // Property definitions with validation patterns
    private static final PropertyDefinition DB_HOST = new PropertyDefinition(
        "db.host",
        "localhost",
        Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9.-]*[a-zA-Z0-9]$"),
        value -> !value.contains(".."),  // No consecutive dots
        "Database hostname or IP address"
    );
    
    private static final PropertyDefinition DB_PORT = new PropertyDefinition(
        "db.port",
        "5432",
        Pattern.compile("^\\d{1,5}$"),
        value -> {
            try {
                int port = Integer.parseInt(value);
                return port >= 1 && port <= 65535;
            } catch (NumberFormatException _) {
                return false;
            }
        },
        "Database port (1-65535)"
    );
    
    private static final PropertyDefinition DB_NAME = new PropertyDefinition(
        "db.name",
        "auth_db",
        Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_-]*$"),
        null,
        "Database name (alphanumeric, underscore, and hyphen)"
    );
    
    private static final PropertyDefinition DB_USER = new PropertyDefinition(
        "db.user",
        "app_user",
        Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_-]*$"),
        null,
        "Database username (alphanumeric, underscore, and hyphen)"
    );
    
    private static final PropertyDefinition DB_PASSWORD = PropertyDefinition.withoutValidation(
        "db.password",
        "password",
        "Database password"
    );
    
    private static final Map<String, PropertyDefinition> PROPERTY_DEFINITIONS = Map.of(
        DB_HOST.key(), DB_HOST,
        DB_PORT.key(), DB_PORT,
        DB_NAME.key(), DB_NAME,
        DB_USER.key(), DB_USER,
        DB_PASSWORD.key(), DB_PASSWORD
    );
    
    private final Properties resolvedProperties;
    
    /**
     * Creates configuration by loading and validating properties.
     * Fails fast if required properties are invalid.
     */
    public DatabaseConfig() {
        this.resolvedProperties = loadAndResolveProperties();
        validateAllDefinedProperties();
    }
    
    /**
     * Get validated property value.
     *
     * @param key The property key
     * @return The property value
     * @throws DatabaseConfigurationException if property is not defined
     */
    public String getProperty(final String key) {
        PropertyDefinition definition = PROPERTY_DEFINITIONS.get(key);
        if (definition == null) {
            throw new DatabaseConfigurationException("Property '" + key + "' is not defined");
        }
        return resolvedProperties.getProperty(key);
    }
    
    /**
     * Get property value with fallback.
     *
     * @param key The property key
     * @param fallback The fallback value
     * @return The property value or fallback
     */
    public String getProperty(final String key, final String fallback) {
        PropertyDefinition definition = PROPERTY_DEFINITIONS.get(key);
        if (definition == null) {
            return fallback;
        }
        return resolvedProperties.getProperty(key, fallback);
    }
    
    /**
     * Get database host.
     */
    public String getHost() {
        return getProperty(DB_HOST.key());
    }
    
    /**
     * Get database port.
     */
    public int getPort() {
        return Integer.parseInt(getProperty(DB_PORT.key()));
    }
    
    /**
     * Get database name.
     */
    public String getDatabaseName() {
        return getProperty(DB_NAME.key());
    }
    
    /**
     * Get database username.
     */
    public String getUsername() {
        return getProperty(DB_USER.key());
    }
    
    /**
     * Get database password.
     */
    public String getPassword() {
        return getProperty(DB_PASSWORD.key());
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
     * Resolve all property placeholders in format ${ENV_VAR:default}.
     */
    private Properties resolveAllProperties(final Properties props) {
        Properties resolved = new Properties();
        props.forEach((key, value) -> 
            resolved.setProperty(
                String.valueOf(key), 
                resolvePropertyValue(String.valueOf(value))
            )
        );
        return resolved;
    }
    
    /**
     * Resolve property value with environment variable or system property substitution.
     */
    private String resolvePropertyValue(final String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        
        String result = value;
        int startIndex = 0;
        
        while ((startIndex = result.indexOf("${", startIndex)) != -1) {
            int endIndex = result.indexOf('}', startIndex);
            if (endIndex == -1) break;
            
            String placeholder = result.substring(startIndex + 2, endIndex);
            String resolvedValue = resolvePlaceholder(placeholder);
            
            result = result.substring(0, startIndex) + resolvedValue + result.substring(endIndex + 1);
            startIndex += resolvedValue.length();
        }
        
        return result;
    }
    
    /**
     * Resolve placeholder in format "ENV_VAR:default_value".
     */
    private String resolvePlaceholder(final String placeholder) {
        String[] parts = placeholder.split(":", 2);
        String varName = parts[0];
        String defaultValue = parts.length > 1 ? parts[1] : "";
        
        return Optional.ofNullable(System.getenv(varName))
            .or(() -> Optional.ofNullable(System.getProperty(varName)))
            .orElse(defaultValue);
    }
    
    /**
     * Validate all defined properties against their patterns.
     */
    private void validateAllDefinedProperties() {
        PROPERTY_DEFINITIONS.forEach((key, definition) -> {
            String rawValue = resolvedProperties.getProperty(key);
            String validatedValue = definition.validatedValue(rawValue);
            resolvedProperties.setProperty(key, validatedValue);
        });
    }
    
    private void logInfo(final Supplier<String> messageSupplier) {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, messageSupplier);
        }
    }
    
    private void logSevere(final String message) {
        LOGGER.log(Level.SEVERE, message);
    }
    
    private void logSevere(final String message, final Throwable throwable) {
        LOGGER.log(Level.SEVERE, message, throwable);
    }
}

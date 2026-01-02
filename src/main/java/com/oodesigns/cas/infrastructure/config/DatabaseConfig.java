package com.oodesigns.cas.infrastructure.config;

import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.oodesigns.cas.util.file.FileLoader;
import com.oodesigns.cas.util.file.FileLoaderException;

/**
 * Database configuration that loads, parses, and validates all required properties.
 * Fails fast on missing, unparsable, or invalid values and exposes typed accessors.
 */
public final class DatabaseConfig {

    private static final Logger LOGGER = Logger.getLogger(DatabaseConfig.class.getName());
    private static final String PROPERTIES_FILE = "application.properties";

    private static final String DB_HOST_KEY = "db.host";
    private static final String DB_PORT_KEY = "db.port";
    private static final String DB_NAME_KEY = "db.name";
    private static final String DB_USER_KEY = "db.user";
    private static final String DB_PASSWORD_KEY = "db.password";

    private static final Set<String> KNOWN_KEYS = Set.of(
        DB_HOST_KEY,
        DB_PORT_KEY,
        DB_NAME_KEY,
        DB_USER_KEY,
        DB_PASSWORD_KEY
    );

    private final Properties validatedProperties;
    private final DatabaseHost host;
    private final DatabasePort port;
    private final DatabaseName databaseName;
    private final DatabaseUser username;
    private final DatabasePassword password;

    /**
     * Creates configuration by loading and validating properties.
     * Fails fast if required properties are invalid.
     */
    public DatabaseConfig() {
        this(loadAndResolveProperties());
    }

    private DatabaseConfig(final Properties resolvedProperties) {
        this.host = buildValue(DB_HOST_KEY, () -> new DatabaseHost(required(resolvedProperties, DB_HOST_KEY)));
        this.port = buildValue(DB_PORT_KEY, () -> new DatabasePort(required(resolvedProperties, DB_PORT_KEY)));
        this.databaseName = buildValue(DB_NAME_KEY, () -> new DatabaseName(required(resolvedProperties, DB_NAME_KEY)));
        this.username = buildValue(DB_USER_KEY, () -> new DatabaseUser(required(resolvedProperties, DB_USER_KEY)));
        this.password = buildValue(DB_PASSWORD_KEY, () -> new DatabasePassword(required(resolvedProperties, DB_PASSWORD_KEY)));
        this.validatedProperties = cacheValidatedProperties();
    }

    /**
     * Get validated property value.
     *
     * @param key The property key
     * @return The property value
     * @throws DatabaseConfigurationException if property is not defined
     */
    public String getProperty(final String key) {
        assertDefinedKey(key);
        return validatedProperties.getProperty(key);
    }

    /**
     * Get property value with fallback.
     *
     * @param key The property key
     * @param fallback The fallback value
     * @return The property value or fallback
     */
    public String getProperty(final String key, final String fallback) {
        if (!KNOWN_KEYS.contains(key)) {
            return fallback;
        }
        return validatedProperties.getProperty(key, fallback);
    }

    /**
     * Get database host.
     */
    public String getHost() {
        return host.value();
    }

    /**
     * Get database port.
     */
    public int getPort() {
        return port.value();
    }

    /**
     * Get database name.
     */
    public String getDatabaseName() {
        return databaseName.value();
    }

    /**
     * Get database username.
     */
    public String getUsername() {
        return username.value();
    }

    /**
     * Get database password.
     */
    public String getPassword() {
        return password.value();
    }

    private Properties cacheValidatedProperties() {
        Properties props = new Properties();
        props.setProperty(DB_HOST_KEY, host.value());
        props.setProperty(DB_PORT_KEY, String.valueOf(port.value()));
        props.setProperty(DB_NAME_KEY, databaseName.value());
        props.setProperty(DB_USER_KEY, username.value());
        props.setProperty(DB_PASSWORD_KEY, password.value());
        return props;
    }

    private static Properties loadAndResolveProperties() {
        return new DatabaseConfigLoader().load();
    }

    private <T> T buildValue(final String key, final Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (final IllegalArgumentException e) {
            throw new DatabaseConfigurationException("Invalid value for '" + key + "': " + e.getMessage(), e);
        }
    }

    private String required(final Properties props, final String key) {
        String value = props.getProperty(key);
        if (value == null) {
            throw new DatabaseConfigurationException("Missing required property '" + key + "'");
        }
        return value;
    }

    private void assertDefinedKey(final String key) {
        if (!KNOWN_KEYS.contains(key)) {
            throw new DatabaseConfigurationException("Property '" + key + "' is not defined");
        }
    }

    private static final class DatabaseConfigLoader {
        private Properties load() {
            return loadPropertiesFile()
                .map(this::resolveAllProperties)
                .orElseThrow(() -> new DatabaseConfigurationException("Failed to load " + PROPERTIES_FILE));
        }

        private Optional<Properties> loadPropertiesFile() {
            try {
                String content = new FileLoader(PROPERTIES_FILE).toString();
                Properties props = new Properties();
                props.load(new StringReader(content));
                logInfo(() -> String.format("Loaded %d properties from %s", props.size(), PROPERTIES_FILE));
                return Optional.of(props);
            } catch (final FileLoaderException e) {
                logSevere("Unable to find " + PROPERTIES_FILE, e);
                return Optional.empty();
            } catch (final IOException e) {
                logSevere("Failed to parse " + PROPERTIES_FILE, e);
                return Optional.empty();
            }
        }

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

        private String resolvePlaceholder(final String placeholder) {
            String[] parts = placeholder.split(":", 2);
            String varName = parts[0];
            String defaultValue = parts.length > 1 ? parts[1] : "";

            return Optional.ofNullable(System.getenv(varName))
                .or(() -> Optional.ofNullable(System.getProperty(varName)))
                .orElse(defaultValue);
        }

        private void logInfo(final Supplier<String> messageSupplier) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO, messageSupplier);
            }
        }

        private void logSevere(final String message, final Throwable throwable) {
            LOGGER.log(Level.SEVERE, message, throwable);
        }
    }
}

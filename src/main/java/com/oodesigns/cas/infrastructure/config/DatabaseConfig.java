package com.oodesigns.cas.infrastructure.config;
import com.oodesigns.cas.util.properties.PropertiesReader;
/**
 * Database configuration that loads, parses, and validates all required properties.
 * Fails fast on missing, unparsable, or invalid values and exposes typed accessors.
 */
public final class DatabaseConfig {
    private final DatabaseHost host;
    private final DatabasePort port;
    private final DatabaseName databaseName;
    private final DatabaseUser username;
    private final DatabasePassword password;

    /**
     * Creates configuration by loading and validating properties.
     * Uses the default PropertiesReaderFactory to create the reader.
     * Fails fast if required properties are invalid.
     */
    public DatabaseConfig(final PropertiesReader propertiesReader) {
        this.host = DatabaseHost.of(propertiesReader.get("db.host"));
        this.databaseName = DatabaseName.of(propertiesReader.get("db.name"));
        this.username = DatabaseUser.of(propertiesReader.get("db.username"));
        this.password = DatabasePassword.of(propertiesReader.get("db.password"));
        this.port = DatabasePort.of(propertiesReader.get("db.port"));
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
    public DatabaseUser getUsername() {
        return username;
    }

    /**
     * Get an independently clearable copy of the database password.
     */
    public DatabasePassword getPassword() {
        return password.copy();
    }
}

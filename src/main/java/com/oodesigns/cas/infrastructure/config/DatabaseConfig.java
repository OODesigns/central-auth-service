package com.oodesigns.cas.infrastructure.config;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.postgresql.ds.PGSimpleDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Database configuration that reads from application.properties
 * and creates JOOQ DSLContext for database operations.
 */
public class DatabaseConfig {
    
    private final Properties properties;
    
    public DatabaseConfig() {
        this.properties = loadProperties();
    }
    
    /**
     * Load application properties from classpath.
     */
    private Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input == null) {
                throw new RuntimeException("Unable to find application.properties");
            }
            props.load(input);
            
            // Replace ${VAR:default} patterns with environment variables
            props.replaceAll((key, value) -> resolveProperty(value.toString()));
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to load application.properties", e);
        }
        return props;
    }
    
    /**
     * Resolve property value with environment variable substitution.
     * Supports format: ${ENV_VAR:default_value}
     */
    private String resolveProperty(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        
        // Simple regex to find ${VAR:default} or ${VAR}
        while (value.contains("${")) {
            int start = value.indexOf("${");
            int end = value.indexOf("}", start);
            if (end == -1) break;
            
            String placeholder = value.substring(start + 2, end);
            String[] parts = placeholder.split(":", 2);
            String envVar = parts[0];
            String defaultVal = parts.length > 1 ? parts[1] : "";
            
            String resolved = System.getenv(envVar);
            if (resolved == null) {
                resolved = System.getProperty(envVar, defaultVal);
            }
            
            value = value.substring(0, start) + resolved + value.substring(end + 1);
        }
        
        return value;
    }
    
    /**
     * Create and configure DSLContext for JOOQ database operations.
     */
    public DSLContext createDslContext() {
        try {
            PGSimpleDataSource dataSource = new PGSimpleDataSource();
            
            String host = properties.getProperty("db.host", "db");
            int port = Integer.parseInt(properties.getProperty("db.port", "5432"));
            String database = properties.getProperty("db.name", "auth_db");
            String user = properties.getProperty("db.user", "app_user");
            String password = properties.getProperty("db.password", "password");
            
            dataSource.setServerNames(new String[]{host});
            dataSource.setPortNumbers(new int[]{port});
            dataSource.setDatabaseName(database);
            dataSource.setUser(user);
            dataSource.setPassword(password);
            
            return DSL.using(dataSource, SQLDialect.POSTGRES);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create DSL context", e);
        }
    }
    
    /**
     * Get a property value.
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    /**
     * Get a property value with default.
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}

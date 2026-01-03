package com.oodesigns.cas.util.properties;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentVariableTransformerTest {

    @Test
    void transformReturnsOriginalValueWhenNoVariables() {
        final EnvironmentVariableTransformer transformer = new EnvironmentVariableTransformer();
        assertEquals("plain value", transformer.apply("plain value"));
    }

    @Test
    void transformReturnsDefaultWhenVariableNotFound() {
        final EnvironmentVariableTransformer transformer = new EnvironmentVariableTransformer();
        assertEquals("default", transformer.apply("${NONEXISTENT_VAR:default}"));
    }

    @Test
    void transformResolvesEnvironmentVariable() {
        final EnvironmentVariableTransformer transformer = new EnvironmentVariableTransformer();
        System.setProperty("TEST_PROP", "test_value");
        
        assertEquals("test_value", transformer.apply("${TEST_PROP:fallback}"));
    }

    @Test
    void transformHandlesMultipleVariables() {
        final EnvironmentVariableTransformer transformer = new EnvironmentVariableTransformer();
        System.setProperty("VAR1", "value1");
        System.setProperty("VAR2", "value2");
        
        assertEquals("value1-value2", transformer.apply("${VAR1:default}-${VAR2:default}"));
    }

    @Test
    void transformReturnsEmptyStringWhenNoDefault() {
        final EnvironmentVariableTransformer transformer = new EnvironmentVariableTransformer();
        assertEquals("", transformer.apply("${NONEXISTENT}"));
    }

    @Test
    void transformHandlesNullValue() {
        final EnvironmentVariableTransformer transformer = new EnvironmentVariableTransformer();
        assertNull(transformer.apply(null));
    }

    @Test
    void transformHandlesMixedContent() {
        final EnvironmentVariableTransformer transformer = new EnvironmentVariableTransformer();
        System.setProperty("HOST", "localhost");
        
        assertEquals("jdbc:mysql://localhost:3306", transformer.apply("jdbc:mysql://${HOST:127.0.0.1}:3306"));
    }

    @Test
    void transformResolvesRealEnvironmentVariable() {
        final EnvironmentVariableTransformer transformer = new EnvironmentVariableTransformer();
        // PATH is a common environment variable that should exist on all systems
        final String pathValue = System.getenv("PATH");
        if (pathValue != null && !pathValue.isEmpty()) {
            assertEquals(pathValue, transformer.apply("${PATH:fallback}"));
        }
    }

    @Test
    void transformFallsBackToSystemPropertyWhenEnvEmpty() {
        final EnvironmentVariableTransformer transformer = new EnvironmentVariableTransformer();
        // Use a variable that definitely doesn't exist as env var
        System.setProperty("UNIQUE_TEST_PROP_12345", "from_property");
        
        assertEquals("from_property", transformer.apply("${UNIQUE_TEST_PROP_12345:default}"));
        
        System.clearProperty("UNIQUE_TEST_PROP_12345");
    }

    @Test
    void transformUsesDefaultWhenBothEnvAndPropertyEmpty() {
        final EnvironmentVariableTransformer transformer = new EnvironmentVariableTransformer();
        // Use a variable name that definitely doesn't exist anywhere
        System.setProperty("EMPTY_PROP_TEST", "");
        
        assertEquals("fallback", transformer.apply("${TOTALLY_NONEXISTENT_VAR_XYZ:fallback}"));
        
        System.clearProperty("EMPTY_PROP_TEST");
    }

    @Test
    void transformHandlesEmptyDefault() {
        final EnvironmentVariableTransformer transformer = new EnvironmentVariableTransformer();
        // Pattern ${VAR:} means empty string as default
        assertEquals("", transformer.apply("${NONEXISTENT_VAR_ABC:}"));
    }

    @Test
    void transformHandlesValueWithoutPlaceholderSyntax() {
        final EnvironmentVariableTransformer transformer = new EnvironmentVariableTransformer();
        // Value that doesn't contain ${ should return as-is quickly
        assertEquals("simple value with $ but no brace", 
            transformer.apply("simple value with $ but no brace"));
    }

    @Test
    void transformFallsBackToDefaultWhenPropertyEmpty() {
        final EnvironmentVariableTransformer transformer = new EnvironmentVariableTransformer();
        // Set an empty system property - should fall back to default
        System.setProperty("EMPTY_SYSTEM_PROP", "");
        
        assertEquals("default_value", transformer.apply("${EMPTY_SYSTEM_PROP:default_value}"));
        
        System.clearProperty("EMPTY_SYSTEM_PROP");
    }

    @Test
    void transformPrefersEnvVarOverSystemProperty() {
        final EnvironmentVariableTransformer transformer = new EnvironmentVariableTransformer();
        // USER is typically set as env var on Unix systems
        final String envUser = System.getenv("USER");
        if (envUser != null && !envUser.isEmpty()) {
            // Set a different system property value
            System.setProperty("USER", "different_user");
            // Environment variable should take precedence
            assertEquals(envUser, transformer.apply("${USER:fallback}"));
            System.clearProperty("USER");
        }
    }

    @Test
    void transformResolvesHomeEnvironmentVariable() {
        final EnvironmentVariableTransformer transformer = new EnvironmentVariableTransformer();
        // HOME is a common environment variable on Unix
        final String home = System.getenv("HOME");
        if (home != null && !home.isEmpty()) {
            assertEquals(home, transformer.apply("${HOME:default}"));
        }
    }

    @Test
    void transformFallsBackWhenEnvVarNotSet() {
        final EnvironmentVariableTransformer transformer = new EnvironmentVariableTransformer();
        // Ensure this variable doesn't exist as env var
        assertNull(System.getenv("DEFINITELY_NOT_AN_ENV_VAR_12345"));
        // And doesn't exist as system property
        assertNull(System.getProperty("DEFINITELY_NOT_AN_ENV_VAR_12345"));
        
        // Should use the default
        assertEquals("my_default", transformer.apply("${DEFINITELY_NOT_AN_ENV_VAR_12345:my_default}"));
    }

    @Test
    void transformEnvVarSetToEmptyStringFallsBackToPropertyOrDefault() {
        // Mock provider: getenv returns empty string, getProperty returns fallback
        EnvironmentVariableTransformer.VariableProvider provider = new EnvironmentVariableTransformer.VariableProvider() {
            @Override
            public String getenv(String name) {
                return "";
            }
            @Override
            public String getProperty(String name) {
                return "fallback";
            }
        };
        EnvironmentVariableTransformer transformer = new EnvironmentVariableTransformer(provider);
        assertEquals("fallback", transformer.apply("${ANY_VAR:fallback}"));
    }
}

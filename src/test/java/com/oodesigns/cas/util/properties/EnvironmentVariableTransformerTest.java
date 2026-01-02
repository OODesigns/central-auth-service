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
}

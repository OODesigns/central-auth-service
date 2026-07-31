package com.oodesigns.cas.util.properties;

import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforms property values by resolving environment variable references.
 * Supports format: ${VARIABLE_NAME:default_value}
 * Checks both environment variables and system properties.
 */
public final class EnvironmentVariableTransformer implements UnaryOperator<String> {

    /**
     * Provider for environment variables and system properties.
     */
    public interface VariableProvider {
        String getenv(String name);
        String getProperty(String name);
    }

    /**
     * Default provider using System.getenv and System.getProperty.
     */
    public static final VariableProvider SYSTEM_PROVIDER = new VariableProvider() {
        @Override
        public String getenv(final String name) {
            return System.getenv(name);
        }
        @Override
        public String getProperty(final String name) {
            return System.getProperty(name);
        }
    };

    /**
     * Regex pattern to match placeholder format: dollar-brace VARIABLE_NAME:default_value close-brace
     * Pattern breakdown:
     * - Literal opening: dollar and brace
     * - Group 1: Variable name (chars excluding colon and brace)
     * - Group 2: Optional default value preceded by colon (chars until brace)
     * - Literal closing: brace
     * Examples matched:
     * - DB_HOST -> varName="DB_HOST", default=null
     * - DB_HOST:localhost -> varName="DB_HOST", default="localhost"
     * - DB_HOST: -> varName="DB_HOST", default=""
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?}");

    private final VariableProvider provider;

    /**
     * Constructs with the default system provider.
     */
    public EnvironmentVariableTransformer() {
        this(SYSTEM_PROVIDER);
    }

    /**
     * Constructs with a custom variable provider (for testing).
     */
    public EnvironmentVariableTransformer(final VariableProvider provider) {
        this.provider = provider;
    }

    /**
     * Transforms a property value by resolving environment variable references.
     * Uses regex pattern matching to find and replace all ${VAR:default} placeholders
     * in a single pass.
     *
     * @param value The property value potentially containing ${VAR:default} references
     * @return The transformed value with environment variables substituted
     */
    @Override
    public String apply(final String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }

        final Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        // Replace all matches with resolved values using regex groups
        return matcher.replaceAll(matchResult -> {
            final String varName = matchResult.group(1);      // Variable name from group 1
            final String defaultValue = matchResult.group(2) != null ? matchResult.group(2) : "";  // Default from group 2, empty string if not provided
            return resolvePlaceholder(varName, defaultValue);
        });
    }

    /**
     * Resolves a single placeholder by checking environment variables and system properties.
     * Resolution order:
     *   1. Environment variable (System.getenv)
     *   2. System property (System.getProperty)
     *   3. Default value (fallback)
     *
     * @param varName The variable name to resolve
     * @param defaultValue The value to use if variable is not found
     * @return The resolved value
     */
    private String resolvePlaceholder(final String varName, final String defaultValue) {
        // Treat empty strings the same as missing to allow defaults to apply
        return Optional.ofNullable(provider.getenv(varName))
            .filter(value -> !value.isEmpty())
            .or(() -> Optional.ofNullable(provider.getProperty(varName)).filter(value -> !value.isEmpty()))
            .orElse(defaultValue);
    }
}

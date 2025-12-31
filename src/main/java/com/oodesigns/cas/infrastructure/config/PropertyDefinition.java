package com.oodesigns.cas.infrastructure.config;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Defines a configuration property with its validation rules.
 * Immutable definition including key name, default value, validation pattern, and description.
 */
public record PropertyDefinition(
    String key,
    String defaultValue,
    Pattern validationPattern,
    Predicate<String> customValidator,
    String description
) {
    
    /**
     * Creates a property definition with both pattern and custom validation.
     */
    public PropertyDefinition {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Property key cannot be null or blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Property description cannot be null or blank");
        }
    }
    
    /**
     * Creates a property definition with regex pattern validation only.
     */
    public static PropertyDefinition withPattern(String key, String defaultValue, Pattern pattern, String description) {
        return new PropertyDefinition(key, defaultValue, pattern, null, description);
    }
    
    /**
     * Creates a property definition with custom validator only.
     */
    public static PropertyDefinition withValidator(String key, String defaultValue, Predicate<String> validator, String description) {
        return new PropertyDefinition(key, defaultValue, null, validator, description);
    }
    
    /**
     * Creates a property definition with no validation (e.g., for passwords).
     */
    public static PropertyDefinition withoutValidation(String key, String defaultValue, String description) {
        return new PropertyDefinition(key, defaultValue, null, null, description);
    }
    
    /**
     * Validates a value against this property's pattern and custom validator.
     *
     * @param value The value to validate
     * @return true if valid or no validation defined, false otherwise
     */
    public boolean isValid(String value) {
        boolean patternValid = validationPattern == null || (value != null && validationPattern.matcher(value).matches());
        boolean customValid = customValidator == null || (value != null && customValidator.test(value));
        return patternValid && customValid;
    }
    
    /**
     * Gets the effective value (provided value or default).
     *
     * @param providedValue The provided value (may be null)
     * @return The provided value if not null, otherwise the default
     */
    public String effectiveValue(String providedValue) {
        return Optional.ofNullable(providedValue).orElse(defaultValue);
    }
    
    /**
     * Validates and returns the effective value.
     *
     * @param providedValue The provided value (may be null)
     * @return The validated effective value
     * @throws DatabaseConfigurationException if validation fails
     */
    public String validatedValue(String providedValue) {
        String value = effectiveValue(providedValue);
        if (!isValid(value)) {
            throw new DatabaseConfigurationException(
                String.format("Invalid value for property '%s': '%s' does not match pattern %s. %s",
                    key, value, validationPattern.pattern(), description)
            );
        }
        return value;
    }
}

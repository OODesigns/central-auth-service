package com.oodesigns.cas.util.validation;

/**
 * Minimal base type for validated immutable values.
 * <p>
 * All validation happens in static factory methods (e.g., {@code of()}) 
 * BEFORE the constructor is called.
 * <p>
 * Subclasses:
 * 1. Have a private constructor that only accepts pre-validated values
 * 2. Implement a public static {@code of()} factory method for creation
 * 3. Perform all validation logic in the factory method before calling constructor
 * <p>
 * Example:
 * <pre>
 * public final class Username extends ValidatedValue&lt;String&gt; {
 *     private Username(String value) {
 *         super(value);
 *     }
 *     
 *     public static Username of(String value) {
 *         if (value == null || value.length() < 3) {
 *             throw new IllegalArgumentException("Invalid username");
 *         }
 *         return new Username(value);
 *     }
 * }
 * </pre>
 */
public abstract class ValidatedValue<T> {

    private final T value;

    /**
     * Protected constructor that accepts an already-validated value.
     * Should only be called from subclass constructors after validation is complete.
     *
     * @param value the pre-validated value
     */
    protected ValidatedValue(final T value) {
        this.value = value;
    }

    /**
     * Get the underlying validated value.
     *
     * @return the stored value
     */
    public final T value() {
        return value;
    }

    @Override
    public final String toString() {
        return String.valueOf(getDisplayValue());
    }

    /**
     * Returns the value to be displayed in toString().
     * Subclasses can override to provide a masked or custom representation.
     * By default, returns the actual value.
     *
     * @return the display value
     */
    protected T getDisplayValue() {
        return value;
    }

    @Override
    public final boolean equals(final Object o) {
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        return value.equals(((ValidatedValue<?>) o).value);
    }

    @Override
    public final int hashCode() {
        return value.hashCode();
    }
}

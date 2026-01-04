package com.oodesigns.cas.util.validation;

/**
 * Base type for values that parse and validate a raw input before use.
 * Subclasses implement parse and validate; failures throw at construction time.
 */
public abstract class ValidatedValue<R, V> {

    private final V value;

    protected ValidatedValue(final R raw) {
        this.value = validate(parse(raw));
    }

    protected abstract V parse(R raw);

    protected abstract V validate(V value);

    public final V value() {
        return value;
    }

    @Override
    public final String toString() {
        return String.valueOf(getDisplayValue());
    }

    /**
     * Returns the value to be displayed in toString().
     * Subclasses can override this to provide a masked or custom representation.
     * By default, returns the actual value.
     */
    protected V getDisplayValue() {
        return value;
    }

    @Override
    public final boolean equals(final Object o) {
        return o != null
            && o.getClass() == getClass()
            && value.equals(((ValidatedValue<?, ?>) o).value);
    }

    @Override
    public final int hashCode() {
        return value.hashCode();
    }
}

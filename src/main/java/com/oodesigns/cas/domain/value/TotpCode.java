package com.oodesigns.cas.domain.value;

import com.oodesigns.cas.util.validation.ValidatedValue;
import java.util.Objects;

/**
 * Value object representing a 6-digit TOTP one-time password code.
 * <p>
 * Validation rules:
 * <ul>
 *   <li>Must not be null.</li>
 *   <li>Must consist of exactly 6 decimal digits ({@code ^\d{6}$}).</li>
 *   <li>Leading zeros are valid — e.g. {@code "005924"} is a real RFC 6238 test vector.</li>
 * </ul>
 */
public final class TotpCode extends ValidatedValue<String> {

    private TotpCode(final String value) {
        super(value);
    }

    /**
     * Factory method to create a {@code TotpCode} with validation.
     *
     * @param value the 6-digit OTP string
     * @return validated {@code TotpCode} instance
     * @throws NullPointerException     if value is null
     * @throws IllegalArgumentException if value is not exactly 6 decimal digits
     */
    public static TotpCode of(final String value) {
        Objects.requireNonNull(value, "TOTP code cannot be null");
        if (!value.matches("^\\d{6}$")) {
            throw new IllegalArgumentException(
                "TOTP code must be exactly 6 decimal digits (received: '" + value + "')");
        }
        return new TotpCode(value);
    }

    /**
     * @return the 6-digit code string
     */
    public String getCode() {
        return value();
    }

    /**
     * Masks the code value in log output to avoid leaking OTP codes.
     */
    @Override
    protected String getDisplayValue() {
        return "***";
    }
}


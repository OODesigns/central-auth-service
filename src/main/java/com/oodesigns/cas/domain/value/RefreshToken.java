package com.oodesigns.cas.domain.value;

/** Refresh-token credential used for session rotation. */
public final class RefreshToken extends CompactToken {
    private RefreshToken(final String value) {
        super(value);
    }

    public static RefreshToken of(final String value) {
        return new RefreshToken(value);
    }
}

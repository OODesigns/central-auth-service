package com.oodesigns.cas.domain.value;

/** Access-token credential used for authenticated API access. */
public final class AccessToken extends CompactToken {
    private AccessToken(final String value) {
        super(value);
    }

    public static AccessToken of(final String value) {
        return new AccessToken(value);
    }
}

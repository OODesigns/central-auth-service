package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.KeyPassword;

/**
 * Supplies KeyPassword instances for JWT signing operations.
 * Implementations should source secrets from secure stores and return
 * KeyPassword objects that can be cleared immediately after use.
 */
@FunctionalInterface
public interface KeySupplier {
    /**
    * Retrieve the password required for signing.
    * Caller must use the returned KeyPassword immediately and clear it afterwards.
     *
     * @return Optional containing secret key material when available
     */
    java.util.Optional<KeyPassword> getPassword();
}

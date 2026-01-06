package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.Credentials;
import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.PasswordHash;
import com.oodesigns.cas.domain.value.UserCredential;
import com.oodesigns.cas.domain.value.UserId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MockPasswordVerifierClearTest {

    @Test
    void clearRemovesRegisteredPasswords() {
        final MockPasswordVerifier verifier = new MockPasswordVerifier();
        final Password password = new Password("secret".toCharArray());
        final PasswordHash hash = verifier.hash(password.chars());
        final UserCredential credential = UserCredential.of(UserId.of(UUID.randomUUID()), hash);

        try (final Credentials credentials = Credentials.of(credential, password)) {
            assertTrue(verifier.verify(credentials).isPresent(), "Credential should verify before clear");
            verifier.clear();
            assertTrue(verifier.verify(credentials).isEmpty(), "Credential should not verify after clear");
        }
    }
}

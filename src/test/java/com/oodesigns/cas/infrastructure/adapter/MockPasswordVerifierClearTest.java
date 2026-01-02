package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.Credentials;
import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.PasswordHash;
import com.oodesigns.cas.domain.value.UserCredential;
import com.oodesigns.cas.domain.value.UserId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MockPasswordVerifierClearTest {

    @Test
    void clearRemovesRegisteredPasswords() {
        MockPasswordVerifier verifier = new MockPasswordVerifier();
        Password password = new Password("secret".toCharArray());
        PasswordHash hash = verifier.hash(password.chars());
        UserCredential credential = new UserCredential(UserId.generate(), hash);

        try (Credentials credentials = new Credentials(credential, password)) {
            assertTrue(verifier.verify(credentials).isPresent(), "Credential should verify before clear");
            verifier.clear();
            assertTrue(verifier.verify(credentials).isEmpty(), "Credential should not verify after clear");
        }
    }
}

package com.oodesigns.cas.infrastructure.adapter;

import com.oodesigns.cas.domain.value.BackupCode;
import com.oodesigns.cas.domain.value.TotpCode;
import com.oodesigns.cas.domain.value.UserId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockTotpVerifierTest {

    @Test
    void verifierChecksEnabledUsersAndConsumesBackupCodes() {
        final MockTotpVerifier verifier = new MockTotpVerifier();
        final UserId userId = UserId.of(UUID.randomUUID());

        verifier.enable(userId);
        verifier.registerValidTotpCode("123456");
        verifier.registerValidBackupCode("ABCD-EFGH-IJKL-MNPQ");

        assertTrue(verifier.isTotpEnabled(userId));
        assertTrue(verifier.verifyCode(userId, TotpCode.of("123456")));
        assertTrue(verifier.verifyBackupCode(userId, BackupCode.of("ABCD-EFGH-IJKL-MNPQ")));
        assertFalse(verifier.verifyBackupCode(userId, BackupCode.of("ABCD-EFGH-IJKL-MNPQ")));
        assertTrue(verifier.getVerificationAttemptCount() >= 1);
        assertTrue(verifier.getBackupAttemptCount() >= 2);
    }

    @Test
    void verifierHandlesNullInputsAndClear() {
        final MockTotpVerifier verifier = new MockTotpVerifier();

        assertFalse(verifier.verifyCode(null, null));
        assertFalse(verifier.verifySetupCode(null, null));
        assertFalse(verifier.verifyBackupCode(null, null));
        assertFalse(verifier.isTotpEnabled(null));

        verifier.clear();
        assertEquals(0, verifier.getVerificationAttemptCount());
        assertEquals(0, verifier.getBackupAttemptCount());
    }

    @Test
    void verifySetupCodeAcceptsPendingCodeWithoutRequiringEnabledUser() {
        final MockTotpVerifier verifier = new MockTotpVerifier();
        final UserId userId = UserId.of(UUID.randomUUID());
        verifier.registerValidTotpCode("654321");

        // Not enabled yet (enrolment in progress) — verifyCode rejects, verifySetupCode accepts.
        assertFalse(verifier.verifyCode(userId, TotpCode.of("654321")));
        assertTrue(verifier.verifySetupCode(userId, TotpCode.of("654321")));
        assertFalse(verifier.verifySetupCode(userId, TotpCode.of("000000")));
    }
}



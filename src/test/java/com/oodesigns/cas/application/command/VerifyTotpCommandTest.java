package com.oodesigns.cas.application.command;

import org.junit.jupiter.api.Test;
import com.oodesigns.cas.domain.value.TwoFactorVerificationToken;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class VerifyTotpCommandTest {

    private static final String VALID_TOKEN = "some.jwt.token";
    private static final String VALID_OTP = "123456";
    private static final String VALID_BACKUP = "ABCD-EFGH-IJKL-MNOP";

    // ---------------------------------------------------------------- valid construction

    @Test
    void constructorAllowsOtpCode() {
        final VerifyTotpCommand cmd = new VerifyTotpCommand(VALID_TOKEN, VALID_OTP);
        assertEquals(VALID_TOKEN, cmd.verificationToken().value());
        assertEquals(VALID_OTP, cmd.code());
        assertTrue(cmd.isOtpCode());
    }

    @Test
    void constructorAllowsBackupCode() {
        final VerifyTotpCommand cmd = new VerifyTotpCommand(VALID_TOKEN, VALID_BACKUP);
        assertEquals(VALID_BACKUP, cmd.code());
        assertFalse(cmd.isOtpCode());
    }

    @Test
    void constructorAllowsLeadingZeroOtp() {
        final VerifyTotpCommand cmd = new VerifyTotpCommand(VALID_TOKEN, "005924");
        assertTrue(cmd.isOtpCode());
    }

    // ---------------------------------------------------------------- null guards

    @Test
    void constructorRejectsNullToken() {
        assertThrows(NullPointerException.class, () -> new VerifyTotpCommand((TwoFactorVerificationToken) null, VALID_OTP));
    }

    @Test
    void constructorRejectsNullCode() {
        assertThrows(NullPointerException.class, () -> new VerifyTotpCommand(VALID_TOKEN, null));
    }

    @Test
    void constructorRejectsBlankToken() {
        assertThrows(IllegalArgumentException.class, () -> new VerifyTotpCommand("  ", VALID_OTP));
        assertThrows(IllegalArgumentException.class, () -> new VerifyTotpCommand("", VALID_OTP));
    }

    // ---------------------------------------------------------------- invalid code formats

    @ParameterizedTest(name = "invalid code: \"{0}\"")
    @ValueSource(strings = {
        "",            // empty
        "12345",       // 5 digits
        "1234567",     // 7 digits
        "abcdef",      // lowercase
        "ABCDEF",      // 6 letters, not digits
        "abcd-efgh-ijkl-mnop",  // lowercase backup
        "ABCD-EFGH-IJKL",       // short backup (3 groups)
        "ABCD-EFGH-IJKL-MNO",   // last group 3 chars
        "ABCD EFGH IJKL MNOP"   // spaces instead of dashes
    })
    void constructorRejectsInvalidCodeFormats(final String badCode) {
        assertThrows(IllegalArgumentException.class,
            () -> new VerifyTotpCommand(VALID_TOKEN, badCode));
    }
}


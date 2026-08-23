package com.oodesigns.cas.application.command;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.Username;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application command handler for initiating TOTP 2FA setup.
 * <p>
 * Orchestrates:
 * <ol>
 *   <li>Generate and persist a TOTP secret via {@link Ports.TotpSetupProvider}.</li>
 *   <li>Build a standard {@code otpauth://totp/} URI so the delivery layer can render a
 *       QR code for the user to scan with their authenticator app.</li>
 * </ol>
 * <p>
 * The secret is returned as part of {@link SetupTotpResult.SuccessResult} for one-time
 * display alongside the QR code (manual-entry fallback). The delivery layer must never
 * log or re-transmit the plaintext secret.
 * <p>
 * This handler only generates/stores the pending secret. The user must confirm the first
 * OTP code via {@code EnableTotpCommandHandler} before 2FA is considered active.
 */
public final class SetupTotpCommandHandler {

    private static final Logger LOGGER = Logger.getLogger(SetupTotpCommandHandler.class.getName());
    private static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    /**
     * TOTP URI parameters that must match {@link com.oodesigns.cas.domain.service.TotpCodeGenerator}:
     * HMAC-SHA1, 6 digits, 30-second time step.
     */
    private static final String TOTP_ALGORITHM = "SHA1";
    private static final int TOTP_DIGITS = 6;
    private static final int TOTP_PERIOD_SECONDS = 30;

    private final Ports.TotpSetupProvider totpSetupProvider;
    private final String issuerName;

    /**
     * @param totpSetupProvider port for secret generation and persistence
     * @param issuerName        human-readable service name shown in the authenticator app
     *                          (e.g. "CentralAuthService"); must not be null or blank
     */
    public SetupTotpCommandHandler(final Ports.TotpSetupProvider totpSetupProvider,
                                   final String issuerName) {
        this.totpSetupProvider = Objects.requireNonNull(totpSetupProvider, "TotpSetupProvider is required");
        Objects.requireNonNull(issuerName, "Issuer name is required");
        if (issuerName.isBlank()) {
            throw new IllegalArgumentException("Issuer name cannot be blank");
        }
        this.issuerName = issuerName;
    }

    /**
     * Handle the setup command: generate a secret and return the {@code otpauth://} URI.
     *
     * @param command the setup command with user ID and username; {@code null} returns
     *                an {@code INVALID_REQUEST} failure
     * @return {@link SetupTotpResult} with success (secret + URI) or failure details
     */
    public SetupTotpResult handle(final SetupTotpCommand command) {
        try {
            return Optional.ofNullable(command)
                .map(this::generateSetup)
                .orElseGet(() -> SetupTotpResult.failure("INVALID_REQUEST",
                    "SetupTotpCommand cannot be null"));
        } catch (final RuntimeException e) {
            LOGGER.log(Level.SEVERE, INTERNAL_ERROR, e);
            return SetupTotpResult.failure(INTERNAL_ERROR, "Failed to set up TOTP: " + e.getMessage());
        }
    }

    private SetupTotpResult generateSetup(final SetupTotpCommand command) {
        final String secret = totpSetupProvider.generateSecret(command.userId());
        final String otpauthUri = buildOtpauthUri(secret, command.username());
        return SetupTotpResult.success(secret, otpauthUri);
    }

    /**
     * Build a standard {@code otpauth://totp/} URI per the Google Authenticator Key URI
     * Format specification.
     * <p>
     * Format: {@code otpauth://totp/{label}?secret={secret}&issuer={issuer}&algorithm={alg}
     * &digits={d}&period={p}}
     * where {@code label} is {@code issuer:account} (both URL-encoded, spaces as {@code %20}).
     *
    * @param secret  Base32-encoded TOTP secret
    * @param account the account username
     * @return the complete {@code otpauth://totp/} URI
     */
    private String buildOtpauthUri(final String secret, final Username account) {
        final String encodedIssuer = urlEncode(issuerName);
        final String encodedAccount = urlEncode(account.value());
        final String label = encodedIssuer + ":" + encodedAccount;

        return "otpauth://totp/" + label
            + "?secret=" + secret
            + "&issuer=" + encodedIssuer
            + "&algorithm=" + TOTP_ALGORITHM
            + "&digits=" + TOTP_DIGITS
            + "&period=" + TOTP_PERIOD_SECONDS;
    }

    /**
     * URL-encode a string using UTF-8, converting {@code +} (form-encoding for spaces) to
     * the RFC 3986 percent-encoded equivalent {@code %20} so the URI is suitable for use in
     * QR codes and deep links.
     */
    private static String urlEncode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}


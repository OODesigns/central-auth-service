package com.oodesigns.cas.infrastructure.grpc;

import com.oodesigns.cas.application.command.DisableReason;
import com.oodesigns.cas.application.command.DisableTotpCommand;
import com.oodesigns.cas.application.command.DisableTotpCommandHandler;
import com.oodesigns.cas.application.command.DisableTotpResult;
import com.oodesigns.cas.application.command.AdminDisableTotpCommand;
import com.oodesigns.cas.application.command.AdminDisableTotpCommandHandler;
import com.oodesigns.cas.application.command.EnableTotpCommand;
import com.oodesigns.cas.application.command.EnableTotpCommandHandler;
import com.oodesigns.cas.application.command.EnableTotpResult;
import com.oodesigns.cas.application.command.LoginCommand;
import com.oodesigns.cas.application.command.LoginCommandHandler;
import com.oodesigns.cas.application.command.LoginResult;
import com.oodesigns.cas.application.command.LogoutCommand;
import com.oodesigns.cas.application.command.LogoutCommandHandler;
import com.oodesigns.cas.application.command.LogoutResult;
import com.oodesigns.cas.application.command.RefreshTokenCommand;
import com.oodesigns.cas.application.command.RefreshTokenCommandHandler;
import com.oodesigns.cas.application.command.RefreshTokenResult;
import com.oodesigns.cas.application.command.SetupTotpCommand;
import com.oodesigns.cas.application.command.SetupTotpCommandHandler;
import com.oodesigns.cas.application.command.SetupTotpResult;
import com.oodesigns.cas.application.command.VerifyTotpCommand;
import com.oodesigns.cas.application.command.VerifyTotpCommandHandler;
import com.oodesigns.cas.application.command.VerifyTotpResult;
import com.oodesigns.cas.domain.value.IpAddress;
import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.TotpCode;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.AccessToken;
import com.oodesigns.cas.domain.value.Username;
import com.oodesigns.cas.infrastructure.grpc.proto.AuthServiceGrpc;
import com.oodesigns.cas.infrastructure.grpc.proto.DisableTotpRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.DisableTotpResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.DisableTotpSuccess;
import com.oodesigns.cas.infrastructure.grpc.proto.AdminDisableTotpRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.AdminDisableTotpResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.AdminDisableTotpSuccess;
import com.oodesigns.cas.infrastructure.grpc.proto.EnableTotpRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.EnableTotpResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.EnableTotpSuccess;
import com.oodesigns.cas.infrastructure.grpc.proto.Error;
import com.oodesigns.cas.infrastructure.grpc.proto.Login2FARequired;
import com.oodesigns.cas.infrastructure.grpc.proto.LoginPasswordResetRequired;
import com.oodesigns.cas.infrastructure.grpc.proto.LoginMfaEnrollmentRequired;
import com.oodesigns.cas.infrastructure.grpc.proto.LoginRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.LoginResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.LoginSuccess;
import com.oodesigns.cas.infrastructure.grpc.proto.LogoutRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.LogoutResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.LogoutSuccess;
import com.oodesigns.cas.infrastructure.grpc.proto.RefreshRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.RefreshResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.RefreshSuccess;
import com.oodesigns.cas.infrastructure.grpc.proto.SetupTotpRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.SetupTotpResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.SetupTotpSuccess;
import com.oodesigns.cas.infrastructure.grpc.proto.VerifyTotpRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.VerifyTotpResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.VerifyTotpSuccess;
import io.grpc.stub.StreamObserver;

import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * gRPC delivery layer for the Central Auth Service.
 * <p>
 * Maps each gRPC method 1-to-1 to the corresponding application command handler.
 * Sits entirely in the {@code infrastructure} layer — domain and application layers
 * remain framework-free.
 * <p>
 * Error handling:
 * <ul>
 *   <li>Business-logic errors are returned as {@code Error} messages inside the response
 *       oneof — the gRPC call always completes normally.</li>
 *   <li>Invalid request field values (e.g. malformed UUID, password too short) throw
 *       during value-object construction and are caught here, returned as
 *       {@code INVALID_REQUEST} errors.</li>
 * </ul>
 */
public final class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

    private static final Logger LOGGER = Logger.getLogger(AuthGrpcService.class.getName());
    private static final String INVALID_REQUEST = "INVALID_REQUEST";

    private final LoginCommandHandler loginHandler;
    private final SetupTotpCommandHandler setupTotpHandler;
    private final EnableTotpCommandHandler enableTotpHandler;
    private final VerifyTotpCommandHandler verifyTotpHandler;
    private final DisableTotpCommandHandler disableTotpHandler;
    private final AdminDisableTotpCommandHandler adminDisableTotpHandler;
    private final RefreshTokenCommandHandler refreshTokenHandler;
        private final LogoutCommandHandler logoutHandler;

    public AuthGrpcService(final LoginCommandHandler loginHandler,
                           final SetupTotpCommandHandler setupTotpHandler,
                           final EnableTotpCommandHandler enableTotpHandler,
                           final VerifyTotpCommandHandler verifyTotpHandler,
                           final DisableTotpCommandHandler disableTotpHandler,
                           final AdminDisableTotpCommandHandler adminDisableTotpHandler,
                           final RefreshTokenCommandHandler refreshTokenHandler,
                           final LogoutCommandHandler logoutHandler) {
        this.loginHandler = Objects.requireNonNull(loginHandler, "LoginCommandHandler is required");
        this.setupTotpHandler = Objects.requireNonNull(setupTotpHandler, "SetupTotpCommandHandler is required");
        this.enableTotpHandler = Objects.requireNonNull(enableTotpHandler, "EnableTotpCommandHandler is required");
        this.verifyTotpHandler = Objects.requireNonNull(verifyTotpHandler, "VerifyTotpCommandHandler is required");
        this.disableTotpHandler = Objects.requireNonNull(disableTotpHandler, "DisableTotpCommandHandler is required");
        this.adminDisableTotpHandler = Objects.requireNonNull(adminDisableTotpHandler, "AdminDisableTotpCommandHandler is required");
        this.refreshTokenHandler = Objects.requireNonNull(refreshTokenHandler, "RefreshTokenCommandHandler is required");
        this.logoutHandler = Objects.requireNonNull(logoutHandler, "LogoutCommandHandler is required");
    }

    // =========================================================================
    // Login
    // =========================================================================

    @Override
    public void login(final LoginRequest request,
                      final StreamObserver<LoginResponse> responseObserver) {
        final LoginResponse response;
        try {
            final String peerIp = GrpcAuthInterceptor.PEER_IP.get();
            if (peerIp == null || peerIp.isBlank()) {
                respond(responseObserver, invalidRequestLoginResponse("Trusted peer IP is unavailable"));
                return;
            }
            final char[] passwordChars = request.getPassword().toCharArray();
            try {
                final LoginCommand command = new LoginCommand(
                    Username.of(request.getUsername()),
                    Password.of(passwordChars),
                    IpAddress.of(peerIp)
                );
                response = toLoginResponse(loginHandler.handle(command));
            } finally {
                Arrays.fill(passwordChars, '\0');
            }
        } catch (final RuntimeException e) {
            LOGGER.log(Level.FINE, "Login request validation failed", e);
            respond(responseObserver, invalidRequestLoginResponse(e.getMessage()));
            return;
        }
        respond(responseObserver, response);
    }

    private LoginResponse toLoginResponse(final LoginResult result) {
        return result.fold(
            success -> LoginResponse.newBuilder()
                    .setSuccess(LoginSuccess.newBuilder()
                    .setAccessToken(success.tokenPair().accessToken().value())
                    .setRefreshToken(success.tokenPair().refreshToken().value())
                    .setUserId(success.userId().asUUID().toString())
                    .addAllPermissions(success.permissions().stream()
                                    .map(p -> p.value())
                                    .toList())
                            .build())
                .build(),
            required2FA -> LoginResponse.newBuilder()
                    .setTotpRequired(Login2FARequired.newBuilder()
                    .setVerificationToken(required2FA.verificationToken().value())
                    .setUserId(required2FA.userId().asUUID().toString())
                            .build())
                .build(),
            passwordReset -> LoginResponse.newBuilder()
                    .setPasswordResetRequired(LoginPasswordResetRequired.newBuilder()
                    .setUserId(passwordReset.userId().asUUID().toString())
                            .build())
                .build(),
            enrollment -> LoginResponse.newBuilder()
                    .setMfaEnrollmentRequired(LoginMfaEnrollmentRequired.newBuilder()
                    .setEnrollmentToken(enrollment.enrollmentToken().value())
                    .setUserId(enrollment.userId().asUUID().toString())
                    .build())
                .build(),
            failure -> LoginResponse.newBuilder()
                .setError(errorMessage(failure.errorCode(), failure.errorMessage()))
                .build());
    }

    private LoginResponse invalidRequestLoginResponse(final String message) {
        return LoginResponse.newBuilder()
                .setError(errorMessage(INVALID_REQUEST, message))
                .build();
    }

    // =========================================================================
    // Setup TOTP
    // =========================================================================

    @Override
    public void setupTotp(final SetupTotpRequest request,
                          final StreamObserver<SetupTotpResponse> responseObserver) {
        final SetupTotpResponse response;
        try {
            if (!matchesPrincipal(request.getUserId())) {
                respond(responseObserver, invalidRequestSetupTotpResponse("Authenticated user does not match user_id"));
                return;
            }
            final SetupTotpCommand command = new SetupTotpCommand(
                    UserId.of(request.getUserId()),
                    Username.of(request.getUsername())
            );
            response = toSetupTotpResponse(setupTotpHandler.handle(command));
        } catch (final RuntimeException e) {
            LOGGER.log(Level.FINE, "SetupTotp request validation failed", e);
            respond(responseObserver, invalidRequestSetupTotpResponse(e.getMessage()));
            return;
        }
        respond(responseObserver, response);
    }

    private SetupTotpResponse toSetupTotpResponse(final SetupTotpResult result) {
        return result.mapTo(s -> SetupTotpResponse.newBuilder()
                        .setSuccess(SetupTotpSuccess.newBuilder()
                                .setSecret(s.secret())
                                .setOtpauthUri(s.otpauthUri())
                                .build())
                        .build())
                .orElse(f -> SetupTotpResponse.newBuilder()
                        .setError(errorMessage(f.errorCode(), f.errorMessage()))
                        .build());
    }

    private SetupTotpResponse invalidRequestSetupTotpResponse(final String message) {
        return SetupTotpResponse.newBuilder()
                .setError(errorMessage(INVALID_REQUEST, message))
                .build();
    }

    // =========================================================================
    // Enable TOTP
    // =========================================================================

    @Override
    public void enableTotp(final EnableTotpRequest request,
                           final StreamObserver<EnableTotpResponse> responseObserver) {
        final EnableTotpResponse response;
        try {
            if (!matchesPrincipal(request.getUserId())) {
                respond(responseObserver, invalidRequestEnableTotpResponse("Authenticated user does not match user_id"));
                return;
            }
            final EnableTotpCommand command = new EnableTotpCommand(
                    UserId.of(request.getUserId()),
                    TotpCode.of(request.getTotpCode())
            );
            response = toEnableTotpResponse(enableTotpHandler.handle(command));
        } catch (final RuntimeException e) {
            LOGGER.log(Level.FINE, "EnableTotp request validation failed", e);
            respond(responseObserver, invalidRequestEnableTotpResponse(e.getMessage()));
            return;
        }
        respond(responseObserver, response);
    }

    private EnableTotpResponse toEnableTotpResponse(final EnableTotpResult result) {
        return result.mapTo(s -> EnableTotpResponse.newBuilder()
                        .setSuccess(EnableTotpSuccess.newBuilder()
                                .addAllBackupCodes(s.backupCodes().stream()
                                        .map(c -> c.getCode())
                                        .toList())
                                .build())
                        .build())
                .orElse(f -> EnableTotpResponse.newBuilder()
                        .setError(errorMessage(f.errorCode(), f.errorMessage()))
                        .build());
    }

    private EnableTotpResponse invalidRequestEnableTotpResponse(final String message) {
        return EnableTotpResponse.newBuilder()
                .setError(errorMessage(INVALID_REQUEST, message))
                .build();
    }

    // =========================================================================
    // Verify TOTP
    // =========================================================================

    @Override
    public void verifyTotp(final VerifyTotpRequest request,
                           final StreamObserver<VerifyTotpResponse> responseObserver) {
        final VerifyTotpResponse response;
        try {
            final VerifyTotpCommand command = new VerifyTotpCommand(
                    request.getVerificationToken(),
                    request.getCode()
            );
            response = toVerifyTotpResponse(verifyTotpHandler.handle(command));
        } catch (final RuntimeException e) {
            LOGGER.log(Level.FINE, "VerifyTotp request validation failed", e);
            respond(responseObserver, invalidRequestVerifyTotpResponse(e.getMessage()));
            return;
        }
        respond(responseObserver, response);
    }

    private VerifyTotpResponse toVerifyTotpResponse(final VerifyTotpResult result) {
        return result.mapTo(s -> VerifyTotpResponse.newBuilder()
                        .setSuccess(VerifyTotpSuccess.newBuilder()
                                .setAccessToken(s.tokenPair().accessToken().value())
                                .setRefreshToken(s.tokenPair().refreshToken().value())
                                .setUserId(s.userId().asUUID().toString())
                                .addAllPermissions(s.permissions().stream()
                                        .map(p -> p.value())
                                        .toList())
                                .build())
                        .build())
                .orElse(f -> VerifyTotpResponse.newBuilder()
                        .setError(errorMessage(f.errorCode(), f.errorMessage()))
                        .build());
    }

    private VerifyTotpResponse invalidRequestVerifyTotpResponse(final String message) {
        return VerifyTotpResponse.newBuilder()
                .setError(errorMessage(INVALID_REQUEST, message))
                .build();
    }

    // =========================================================================
    // Refresh (refresh-token rotation)
    // =========================================================================

    @Override
    public void refresh(final RefreshRequest request,
                        final StreamObserver<RefreshResponse> responseObserver) {
        final RefreshResponse response;
        try {
            final RefreshTokenCommand command = new RefreshTokenCommand(request.getRefreshToken());
            response = toRefreshResponse(refreshTokenHandler.handle(command));
        } catch (final RuntimeException e) {
            LOGGER.log(Level.FINE, "Refresh request validation failed", e);
            respond(responseObserver, invalidRequestRefreshResponse(e.getMessage()));
            return;
        }
        respond(responseObserver, response);
    }

    private RefreshResponse toRefreshResponse(final RefreshTokenResult result) {
        return result.mapTo(s -> RefreshResponse.newBuilder()
                        .setSuccess(RefreshSuccess.newBuilder()
                                .setAccessToken(s.tokenPair().accessToken().value())
                                .setRefreshToken(s.tokenPair().refreshToken().value())
                                .setUserId(s.userId().asUUID().toString())
                                .addAllPermissions(s.permissions().stream()
                                        .map(p -> p.value())
                                        .toList())
                                .build())
                        .build())
                .orElse(f -> RefreshResponse.newBuilder()
                        .setError(errorMessage(f.errorCode(), f.errorMessage()))
                        .build());
    }

    private RefreshResponse invalidRequestRefreshResponse(final String message) {
        return RefreshResponse.newBuilder()
                .setError(errorMessage(INVALID_REQUEST, message))
                .build();
    }

    // =========================================================================
    // Logout
    // =========================================================================

    @Override
    public void logout(final LogoutRequest request,
                       final StreamObserver<LogoutResponse> responseObserver) {
        final LogoutResponse response;
        try {
            if (GrpcAuthInterceptor.bearerToken() != null
                    && !GrpcAuthInterceptor.bearerToken().equals(request.getAccessToken())) {
                respond(responseObserver, invalidRequestLogoutResponse("Access token does not match bearer token"));
                return;
            }
            final LogoutCommand command = new LogoutCommand(AccessToken.of(request.getAccessToken()));
            response = toLogoutResponse(logoutHandler.handle(command));
        } catch (final RuntimeException e) {
            LOGGER.log(Level.FINE, "Logout request validation failed", e);
            respond(responseObserver, invalidRequestLogoutResponse(e.getMessage()));
            return;
        }
        respond(responseObserver, response);
    }

    private LogoutResponse toLogoutResponse(final LogoutResult result) {
        return result.mapTo(success -> LogoutResponse.newBuilder()
                        .setSuccess(LogoutSuccess.getDefaultInstance())
                        .build())
                .orElse(failure -> LogoutResponse.newBuilder()
                        .setError(errorMessage(failure.errorCode(), failure.errorMessage()))
                        .build());
    }

    private LogoutResponse invalidRequestLogoutResponse(final String message) {
        return LogoutResponse.newBuilder()
                .setError(errorMessage(INVALID_REQUEST, message))
                .build();
    }

    // =========================================================================
    // Disable TOTP
    // =========================================================================

    @Override
    public void disableTotp(final DisableTotpRequest request,
                            final StreamObserver<DisableTotpResponse> responseObserver) {
        final DisableTotpResponse response;
        try {
            if (GrpcAuthInterceptor.isEnrollmentToken()) {
                respond(responseObserver, invalidRequestDisableTotpResponse("An access token is required"));
                return;
            }
            if (!matchesPrincipal(request.getUserId())) {
                respond(responseObserver, invalidRequestDisableTotpResponse("Authenticated user does not match user_id"));
                return;
            }
            final char[] passwordChars = request.getPassword().toCharArray();
            try {
                final DisableReason reason = toDomainReason(request.getReason());
                if (reason == null) {
                    respond(responseObserver, invalidRequestDisableTotpResponse(
                        "Disable reason must be specified"));
                    return;
                }
                final boolean ownAccount = matchesPrincipal(request.getUserId());
                final boolean privileged = GrpcAuthInterceptor.hasPermission("manage_mfa");
                if ((!ownAccount && !privileged) || (reason != DisableReason.USER_REQUESTED && !privileged)) {
                    respond(responseObserver, invalidRequestDisableTotpResponse(
                        "Privileged disable reasons require administrative authorization"));
                    return;
                }
                final DisableTotpCommand command = new DisableTotpCommand(
                        UserId.of(request.getUserId()),
                        Password.of(passwordChars),
                        reason
                );
                response = toDisableTotpResponse(disableTotpHandler.handle(command));
            } finally {
                Arrays.fill(passwordChars, '\0');
            }
        } catch (final RuntimeException e) {
            LOGGER.log(Level.FINE, "DisableTotp request validation failed", e);
            respond(responseObserver, invalidRequestDisableTotpResponse(e.getMessage()));
            return;
        }
        respond(responseObserver, response);
    }

    private DisableReason toDomainReason(
            final com.oodesigns.cas.infrastructure.grpc.proto.DisableReason protoReason) {
        return switch (protoReason) {
            case USER_REQUESTED    -> DisableReason.USER_REQUESTED;
            case ADMIN_FORCED      -> DisableReason.ADMIN_FORCED;
            case SECURITY_INCIDENT -> DisableReason.SECURITY_INCIDENT;
            case RECOVERY_FLOW     -> DisableReason.RECOVERY_FLOW;
            default                -> null;
        };
    }

    private DisableTotpResponse toDisableTotpResponse(final DisableTotpResult result) {
        return result.mapTo(s -> DisableTotpResponse.newBuilder()
                        .setSuccess(DisableTotpSuccess.getDefaultInstance())
                        .build())
                .orElse(f -> DisableTotpResponse.newBuilder()
                        .setError(errorMessage(f.errorCode(), f.errorMessage()))
                        .build());
    }

    private DisableTotpResponse invalidRequestDisableTotpResponse(final String message) {
        return DisableTotpResponse.newBuilder()
                .setError(errorMessage(INVALID_REQUEST, message))
                .build();
    }

    @Override
    public void adminDisableTotp(final AdminDisableTotpRequest request,
                                 final StreamObserver<AdminDisableTotpResponse> responseObserver) {
        try {
            if (GrpcAuthInterceptor.isEnrollmentToken()) {
                responseObserver.onError(io.grpc.Status.UNAUTHENTICATED
                    .withDescription("An access token is required")
                    .asRuntimeException());
                return;
            }
            if (!GrpcAuthInterceptor.hasPermission("manage_mfa")) {
                responseObserver.onError(io.grpc.Status.PERMISSION_DENIED
                    .withDescription("manage_mfa permission is required")
                    .asRuntimeException());
                return;
            }
            final UserId adminId = GrpcAuthInterceptor.principal();
            if (adminId == null) {
                responseObserver.onError(io.grpc.Status.UNAUTHENTICATED
                    .withDescription("Authenticated administrator is required")
                    .asRuntimeException());
                return;
            }
            final DisableReason reason = toDomainReason(request.getReason());
            if (reason == null || reason == DisableReason.USER_REQUESTED) {
                responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("A privileged disable reason is required")
                    .asRuntimeException());
                return;
            }
            final char[] passwordChars = request.getAdminPassword().toCharArray();
            try {
                final AdminDisableTotpCommand command = new AdminDisableTotpCommand(
                    adminId, Password.of(passwordChars), UserId.of(request.getTargetUserId()), reason);
                respond(responseObserver, toAdminDisableTotpResponse(adminDisableTotpHandler.handle(command)));
            } finally {
                Arrays.fill(passwordChars, '\0');
            }
        } catch (final IllegalArgumentException exception) {
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                .withDescription(exception.getMessage()).asRuntimeException());
        } catch (final RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "AdminDisableTotp request failed", exception);
            responseObserver.onError(io.grpc.Status.INTERNAL
                .withDescription("Admin TOTP disable failed").asRuntimeException());
        }
    }

    private AdminDisableTotpResponse toAdminDisableTotpResponse(final DisableTotpResult result) {
        return result.mapTo(success -> AdminDisableTotpResponse.newBuilder()
                .setSuccess(AdminDisableTotpSuccess.getDefaultInstance()).build())
            .orElse(failure -> AdminDisableTotpResponse.newBuilder()
                .setError(errorMessage(failure.errorCode(), failure.errorMessage())).build());
    }

    private boolean matchesPrincipal(final String requestedUserId) {
        final UserId principal = GrpcAuthInterceptor.principal();
        return principal != null && principal.equals(UserId.of(requestedUserId));
    }

    // =========================================================================
    // Shared helpers
    // =========================================================================

    private Error errorMessage(final String code, final String message) {
        return Error.newBuilder()
                .setErrorCode(code)
                .setErrorMessage(message)
                .build();
    }

    private <T> void respond(final StreamObserver<T> observer, final T response) {
        observer.onNext(response);
        observer.onCompleted();
    }
}


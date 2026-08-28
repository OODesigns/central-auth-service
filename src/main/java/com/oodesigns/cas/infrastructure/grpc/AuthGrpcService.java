package com.oodesigns.cas.infrastructure.grpc;

import com.google.protobuf.Any;
import com.google.rpc.ErrorInfo;
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
import com.oodesigns.cas.application.command.IssueRecoveryTokenCommand;
import com.oodesigns.cas.application.command.IssueRecoveryTokenCommandHandler;
import com.oodesigns.cas.application.command.IssueRecoveryTokenResult;
import com.oodesigns.cas.application.command.CompleteRecoveryCommand;
import com.oodesigns.cas.application.command.CompleteRecoveryCommandHandler;
import com.oodesigns.cas.application.command.CompleteRecoveryResult;
import com.oodesigns.cas.domain.value.IpAddress;
import com.oodesigns.cas.domain.value.Password;
import com.oodesigns.cas.domain.value.TotpCode;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.AccessToken;
import com.oodesigns.cas.domain.value.Username;
import com.oodesigns.cas.domain.value.RecoveryToken;
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
import com.oodesigns.cas.infrastructure.grpc.proto.IssueRecoveryTokenRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.IssueRecoveryTokenResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.IssueRecoveryTokenSuccess;
import com.oodesigns.cas.infrastructure.grpc.proto.CompleteRecoveryRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.CompleteRecoveryResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.CompleteRecoverySuccess;
import io.grpc.stub.StreamObserver;
import io.grpc.Status;
import io.grpc.protobuf.StatusProto;

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
 * Error handling uses canonical gRPC statuses with standard {@code google.rpc.Status}
 * and {@code ErrorInfo} details. Successful responses retain their existing payloads.
 */
public final class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

    private static final Logger LOGGER = Logger.getLogger(AuthGrpcService.class.getName());
    private final LoginCommandHandler loginHandler;
    private final SetupTotpCommandHandler setupTotpHandler;
    private final EnableTotpCommandHandler enableTotpHandler;
    private final VerifyTotpCommandHandler verifyTotpHandler;
    private final DisableTotpCommandHandler disableTotpHandler;
    private final AdminDisableTotpCommandHandler adminDisableTotpHandler;
    private final RefreshTokenCommandHandler refreshTokenHandler;
    private final LogoutCommandHandler logoutHandler;
    private IssueRecoveryTokenCommandHandler issueRecoveryTokenHandler;
    private CompleteRecoveryCommandHandler completeRecoveryHandler;

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

        public AuthGrpcService(final LoginCommandHandler loginHandler,
                   final SetupTotpCommandHandler setupTotpHandler,
                   final EnableTotpCommandHandler enableTotpHandler,
                   final VerifyTotpCommandHandler verifyTotpHandler,
                   final DisableTotpCommandHandler disableTotpHandler,
                   final AdminDisableTotpCommandHandler adminDisableTotpHandler,
                   final RefreshTokenCommandHandler refreshTokenHandler,
                   final LogoutCommandHandler logoutHandler,
                   final IssueRecoveryTokenCommandHandler issueRecoveryTokenHandler,
                   final CompleteRecoveryCommandHandler completeRecoveryHandler) {
        this(loginHandler, setupTotpHandler, enableTotpHandler, verifyTotpHandler,
            disableTotpHandler, adminDisableTotpHandler, refreshTokenHandler, logoutHandler);
        this.issueRecoveryTokenHandler = Objects.requireNonNull(issueRecoveryTokenHandler,
            "IssueRecoveryTokenCommandHandler is required");
        this.completeRecoveryHandler = Objects.requireNonNull(completeRecoveryHandler,
            "CompleteRecoveryCommandHandler is required");
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
                fail(responseObserver, Status.Code.INVALID_ARGUMENT, "Trusted peer IP is unavailable");
                return;
            }
            final char[] passwordChars = request.getPassword().toCharArray();
            try {
                final LoginCommand command = new LoginCommand(
                    Username.of(request.getUsername()),
                    Password.of(passwordChars),
                    IpAddress.of(peerIp)
                );
                response = toLoginResponse(loginHandler.handle(command), responseObserver);
            } finally {
                Arrays.fill(passwordChars, '\0');
            }
        } catch (final RuntimeException e) {
            LOGGER.log(Level.FINE, "Login request validation failed", e);
            fail(responseObserver, Status.Code.INVALID_ARGUMENT, e.getMessage());
            return;
        }
        respondIfPresent(responseObserver, response);
    }

    private LoginResponse toLoginResponse(final LoginResult result,
                                          final StreamObserver<LoginResponse> responseObserver) {
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
            failure -> fail(responseObserver, statusCode(failure.errorCode()), failure.errorCode(), failure.errorMessage()));
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
                fail(responseObserver, Status.Code.PERMISSION_DENIED, "Authenticated user does not match user_id");
                return;
            }
            final SetupTotpCommand command = new SetupTotpCommand(
                    UserId.of(request.getUserId()),
                    Username.of(request.getUsername())
            );
            response = toSetupTotpResponse(setupTotpHandler.handle(command), responseObserver);
        } catch (final RuntimeException e) {
            LOGGER.log(Level.FINE, "SetupTotp request validation failed", e);
            fail(responseObserver, Status.Code.INVALID_ARGUMENT, e.getMessage());
            return;
        }
        respondIfPresent(responseObserver, response);
    }

    private SetupTotpResponse toSetupTotpResponse(final SetupTotpResult result,
                                                  final StreamObserver<SetupTotpResponse> responseObserver) {
        return result.mapTo(s -> SetupTotpResponse.newBuilder()
                        .setSuccess(SetupTotpSuccess.newBuilder()
                                .setSecret(s.secret())
                                .setOtpauthUri(s.otpauthUri())
                                .build())
                        .build())
                .orElse(f -> fail(responseObserver, statusCode(f.errorCode()), f.errorCode(), f.errorMessage()));
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
                fail(responseObserver, Status.Code.PERMISSION_DENIED, "Authenticated user does not match user_id");
                return;
            }
            final EnableTotpCommand command = new EnableTotpCommand(
                    UserId.of(request.getUserId()),
                    TotpCode.of(request.getTotpCode())
            );
            response = toEnableTotpResponse(enableTotpHandler.handle(command), responseObserver);
        } catch (final RuntimeException e) {
            LOGGER.log(Level.FINE, "EnableTotp request validation failed", e);
            fail(responseObserver, Status.Code.INVALID_ARGUMENT, e.getMessage());
            return;
        }
        respondIfPresent(responseObserver, response);
    }

    private EnableTotpResponse toEnableTotpResponse(final EnableTotpResult result,
                                                    final StreamObserver<EnableTotpResponse> responseObserver) {
        return result.mapTo(s -> EnableTotpResponse.newBuilder()
                        .setSuccess(EnableTotpSuccess.newBuilder()
                                .addAllBackupCodes(s.backupCodes().stream()
                                        .map(c -> c.getCode())
                                        .toList())
                                .build())
                        .build())
                .orElse(f -> fail(responseObserver, statusCode(f.errorCode()), f.errorCode(), f.errorMessage()));
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
            response = toVerifyTotpResponse(verifyTotpHandler.handle(command), responseObserver);
        } catch (final RuntimeException e) {
            LOGGER.log(Level.FINE, "VerifyTotp request validation failed", e);
            fail(responseObserver, Status.Code.INVALID_ARGUMENT, e.getMessage());
            return;
        }
        respondIfPresent(responseObserver, response);
    }

    private VerifyTotpResponse toVerifyTotpResponse(final VerifyTotpResult result,
                                                    final StreamObserver<VerifyTotpResponse> responseObserver) {
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
                .orElse(f -> fail(responseObserver, statusCode(f.errorCode()), f.errorCode(), f.errorMessage()));
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
            response = toRefreshResponse(refreshTokenHandler.handle(command), responseObserver);
        } catch (final RuntimeException e) {
            LOGGER.log(Level.FINE, "Refresh request validation failed", e);
            fail(responseObserver, Status.Code.INVALID_ARGUMENT, e.getMessage());
            return;
        }
        respondIfPresent(responseObserver, response);
    }

    private RefreshResponse toRefreshResponse(final RefreshTokenResult result,
                                              final StreamObserver<RefreshResponse> responseObserver) {
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
                .orElse(f -> fail(responseObserver, statusCode(f.errorCode()), f.errorCode(), f.errorMessage()));
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
                fail(responseObserver, Status.Code.UNAUTHENTICATED, "Access token does not match bearer token");
                return;
            }
            final LogoutCommand command = new LogoutCommand(AccessToken.of(request.getAccessToken()));
            response = toLogoutResponse(logoutHandler.handle(command), responseObserver);
        } catch (final RuntimeException e) {
            LOGGER.log(Level.FINE, "Logout request validation failed", e);
            fail(responseObserver, Status.Code.INVALID_ARGUMENT, e.getMessage());
            return;
        }
        respondIfPresent(responseObserver, response);
    }

    private LogoutResponse toLogoutResponse(final LogoutResult result,
                                            final StreamObserver<LogoutResponse> responseObserver) {
        return result.mapTo(success -> LogoutResponse.newBuilder()
                        .setSuccess(LogoutSuccess.getDefaultInstance())
                        .build())
                .orElse(failure -> fail(responseObserver, statusCode(failure.errorCode()), failure.errorCode(), failure.errorMessage()));
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
                fail(responseObserver, Status.Code.UNAUTHENTICATED, "An access token is required");
                return;
            }
            if (!matchesPrincipal(request.getUserId())) {
                fail(responseObserver, Status.Code.PERMISSION_DENIED, "Authenticated user does not match user_id");
                return;
            }
            final char[] passwordChars = request.getPassword().toCharArray();
            try {
                final DisableReason reason = toDomainReason(request.getReason());
                if (reason == null) {
                    fail(responseObserver, Status.Code.INVALID_ARGUMENT, "Disable reason must be specified");
                    return;
                }
                final boolean privileged = GrpcAuthInterceptor.hasPermission("manage_mfa");
                if (reason != DisableReason.USER_REQUESTED && !privileged) {
                    fail(responseObserver, Status.Code.PERMISSION_DENIED,
                        "Privileged disable reasons require administrative authorization");
                    return;
                }
                final DisableTotpCommand command = new DisableTotpCommand(
                        UserId.of(request.getUserId()),
                        Password.of(passwordChars),
                        reason
                );
                response = toDisableTotpResponse(disableTotpHandler.handle(command), responseObserver);
            } finally {
                Arrays.fill(passwordChars, '\0');
            }
        } catch (final RuntimeException e) {
            LOGGER.log(Level.FINE, "DisableTotp request validation failed", e);
            fail(responseObserver, Status.Code.INVALID_ARGUMENT, e.getMessage());
            return;
        }
        respondIfPresent(responseObserver, response);
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

    private DisableTotpResponse toDisableTotpResponse(final DisableTotpResult result,
                                                      final StreamObserver<DisableTotpResponse> responseObserver) {
        return result.mapTo(s -> DisableTotpResponse.newBuilder()
                        .setSuccess(DisableTotpSuccess.getDefaultInstance())
                        .build())
                .orElse(f -> fail(responseObserver, statusCode(f.errorCode()), f.errorCode(), f.errorMessage()));
    }

    @Override
    public void adminDisableTotp(final AdminDisableTotpRequest request,
                                 final StreamObserver<AdminDisableTotpResponse> responseObserver) {
        try {
            if (GrpcAuthInterceptor.isEnrollmentToken()) {
                fail(responseObserver, Status.Code.UNAUTHENTICATED, "An access token is required");
                return;
            }
            if (!GrpcAuthInterceptor.hasPermission("manage_mfa")) {
                fail(responseObserver, Status.Code.PERMISSION_DENIED, "manage_mfa permission is required");
                return;
            }
            final UserId adminId = GrpcAuthInterceptor.principal();
            if (adminId == null) {
                fail(responseObserver, Status.Code.UNAUTHENTICATED, "Authenticated administrator is required");
                return;
            }
            final DisableReason reason = toDomainReason(request.getReason());
            if (reason == null || reason == DisableReason.USER_REQUESTED) {
                fail(responseObserver, Status.Code.INVALID_ARGUMENT, "A privileged disable reason is required");
                return;
            }
            final char[] passwordChars = request.getAdminPassword().toCharArray();
            try {
                final AdminDisableTotpCommand command = new AdminDisableTotpCommand(
                    adminId, Password.of(passwordChars), UserId.of(request.getTargetUserId()), reason);
                final AdminDisableTotpResponse response =
                    toAdminDisableTotpResponse(adminDisableTotpHandler.handle(command), responseObserver);
                respondIfPresent(responseObserver, response);
            } finally {
                Arrays.fill(passwordChars, '\0');
            }
        } catch (final IllegalArgumentException exception) {
            fail(responseObserver, Status.Code.INVALID_ARGUMENT, exception.getMessage());
        } catch (final RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "AdminDisableTotp request failed", exception);
            fail(responseObserver, Status.Code.INTERNAL, "Admin TOTP disable failed");
        }
    }

    private AdminDisableTotpResponse toAdminDisableTotpResponse(
            final DisableTotpResult result,
            final StreamObserver<AdminDisableTotpResponse> responseObserver) {
        return result.mapTo(success -> AdminDisableTotpResponse.newBuilder()
                .setSuccess(AdminDisableTotpSuccess.getDefaultInstance()).build())
            .orElse(failure -> fail(responseObserver, statusCode(failure.errorCode()), failure.errorCode(), failure.errorMessage()));
    }

    @Override
    public void issueRecoveryToken(final IssueRecoveryTokenRequest request,
                                   final StreamObserver<IssueRecoveryTokenResponse> responseObserver) {
        try {
            if (GrpcAuthInterceptor.isEnrollmentToken()) {
                fail(responseObserver, Status.Code.UNAUTHENTICATED, "An access token is required");
                return;
            }
            if (!GrpcAuthInterceptor.hasPermission("manage_recovery")) {
                fail(responseObserver, Status.Code.PERMISSION_DENIED, "manage_recovery permission is required");
                return;
            }
            final UserId administratorId = GrpcAuthInterceptor.principal();
            if (administratorId == null || issueRecoveryTokenHandler == null) {
                fail(responseObserver, Status.Code.UNAUTHENTICATED, "Authenticated administrator is required");
                return;
            }
            final IssueRecoveryTokenResult result = issueRecoveryTokenHandler.handle(new IssueRecoveryTokenCommand(
                    administratorId, UserId.of(request.getTargetUserId())));
            respondIfPresent(responseObserver, result.fold(
                success -> IssueRecoveryTokenResponse.newBuilder().setSuccess(
                    IssueRecoveryTokenSuccess.newBuilder().setRecoveryToken(success.token().value()).build()).build(),
                failure -> fail(responseObserver, statusCode(failure.errorCode()), failure.errorCode(), failure.errorMessage())));
        } catch (final RuntimeException exception) {
            fail(responseObserver, Status.Code.INVALID_ARGUMENT, "Invalid recovery issuance request");
        }
    }

    @Override
    public void completeRecovery(final CompleteRecoveryRequest request,
                                 final StreamObserver<CompleteRecoveryResponse> responseObserver) {
        try {
            if (completeRecoveryHandler == null) {
                fail(responseObserver, Status.Code.UNAVAILABLE, "Account recovery is unavailable");
                return;
            }
            final char[] passwordChars = request.getNewPassword().toCharArray();
            try {
                final CompleteRecoveryResult result = completeRecoveryHandler.handle(new CompleteRecoveryCommand(
                        RecoveryToken.of(request.getRecoveryToken()), Password.of(passwordChars)));
                respondIfPresent(responseObserver, result.fold(
                    success -> CompleteRecoveryResponse.newBuilder()
                        .setSuccess(CompleteRecoverySuccess.getDefaultInstance()).build(),
                    failure -> fail(responseObserver, statusCode(failure.errorCode()), failure.errorCode(), failure.errorMessage())));
            } finally {
                Arrays.fill(passwordChars, '\0');
            }
        } catch (final RuntimeException exception) {
            fail(responseObserver, Status.Code.INVALID_ARGUMENT, "Invalid recovery completion request");
        }
    }

    private boolean matchesPrincipal(final String requestedUserId) {
        final UserId principal = GrpcAuthInterceptor.principal();
        return principal != null && principal.equals(UserId.of(requestedUserId));
    }

    // =========================================================================
    // Shared helpers
    // =========================================================================

    private <T> void respond(final StreamObserver<T> observer, final T response) {
        observer.onNext(response);
        observer.onCompleted();
    }

    private <T> void respondIfPresent(final StreamObserver<T> observer, final T response) {
        if (response != null) {
            respond(observer, response);
        }
    }

        private <T> T fail(final StreamObserver<T> observer,
                   final Status.Code code,
                   final String message) {
            return fail(observer, code, code.name(), message);
        }

        private <T> T fail(final StreamObserver<T> observer,
                           final Status.Code code,
                           final String reason,
                           final String message) {
        final String safeMessage = message == null || message.isBlank() ? "Request failed" : message;
        final com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
            .setCode(code.value())
            .setMessage(safeMessage)
            .addDetails(Any.pack(ErrorInfo.newBuilder()
                .setReason(reason)
                .setDomain("central-auth-service")
                .build()))
            .build();
        observer.onError(StatusProto.toStatusRuntimeException(status));
        return null;
    }

    private Status.Code statusCode(final String errorCode) {
        return switch (errorCode) {
            case "INVALID_CREDENTIALS", "INVALID_ACCESS_TOKEN", "INVALID_PASSWORD",
                 "INVALID_TOTP_CODE", "INVALID_2FA_TOKEN", "INVALID_REFRESH_TOKEN",
                 "REFRESH_TOKEN_REUSE_DETECTED", "INVALID_RECOVERY_TOKEN" -> Status.Code.UNAUTHENTICATED;
            case "RATE_LIMITED" -> Status.Code.RESOURCE_EXHAUSTED;
            case "INTERNAL_ERROR" -> Status.Code.INTERNAL;
            case "MFA_SETUP_REQUIRED", "MFA_VERIFICATION_REQUIRED" -> Status.Code.FAILED_PRECONDITION;
            default -> Status.Code.INVALID_ARGUMENT;
        };
    }
}


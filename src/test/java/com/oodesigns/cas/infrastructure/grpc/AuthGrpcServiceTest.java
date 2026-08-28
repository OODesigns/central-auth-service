package com.oodesigns.cas.infrastructure.grpc;

import com.oodesigns.cas.application.command.DisableTotpCommandHandler;
import com.oodesigns.cas.application.command.AdminDisableTotpCommandHandler;
import com.oodesigns.cas.application.command.DisableReason;
import com.oodesigns.cas.application.command.DisableTotpResult;
import com.oodesigns.cas.application.command.EnableTotpCommandHandler;
import com.oodesigns.cas.application.command.EnableTotpResult;
import com.oodesigns.cas.application.command.CompleteRecoveryCommandHandler;
import com.oodesigns.cas.application.command.CompleteRecoveryResult;
import com.oodesigns.cas.application.command.IssueRecoveryTokenCommandHandler;
import com.oodesigns.cas.application.command.IssueRecoveryTokenResult;
import com.oodesigns.cas.application.command.LoginCommandHandler;
import com.oodesigns.cas.application.command.LoginResult;
import com.oodesigns.cas.application.command.LogoutCommandHandler;
import com.oodesigns.cas.application.command.LogoutResult;
import com.oodesigns.cas.application.command.RefreshTokenCommandHandler;
import com.oodesigns.cas.application.command.RefreshTokenResult;
import com.oodesigns.cas.application.command.SetupTotpCommandHandler;
import com.oodesigns.cas.application.command.SetupTotpResult;
import com.oodesigns.cas.application.command.VerifyTotpCommandHandler;
import com.oodesigns.cas.application.command.VerifyTotpResult;
import com.oodesigns.cas.domain.service.TokenService;
import com.oodesigns.cas.domain.value.BackupCode;
import com.oodesigns.cas.domain.value.Permission;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.AccessToken;
import com.oodesigns.cas.domain.value.RefreshToken;
import com.oodesigns.cas.domain.value.TwoFactorVerificationToken;
import com.oodesigns.cas.infrastructure.grpc.proto.DisableTotpRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.DisableTotpResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.AdminDisableTotpRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.AdminDisableTotpResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.EnableTotpRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.EnableTotpResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.LoginRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.LoginResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.LogoutRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.LogoutResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.RefreshRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.RefreshResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.SetupTotpRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.SetupTotpResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.VerifyTotpRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.VerifyTotpResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.IssueRecoveryTokenRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.IssueRecoveryTokenResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.CompleteRecoveryRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.CompleteRecoveryResponse;
import io.grpc.stub.StreamObserver;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"varargs", "unchecked"})
class AuthGrpcServiceTest {

    @Mock private LoginCommandHandler loginHandler;
    @Mock private SetupTotpCommandHandler setupTotpHandler;
    @Mock private EnableTotpCommandHandler enableTotpHandler;
    @Mock private VerifyTotpCommandHandler verifyTotpHandler;
    @Mock private DisableTotpCommandHandler disableTotpHandler;
        @Mock private AdminDisableTotpCommandHandler adminDisableTotpHandler;
    @Mock private RefreshTokenCommandHandler refreshTokenHandler;
        @Mock private LogoutCommandHandler logoutHandler;
        @Mock private IssueRecoveryTokenCommandHandler issueRecoveryTokenHandler;
        @Mock private CompleteRecoveryCommandHandler completeRecoveryHandler;

    @Mock private StreamObserver<LoginResponse> loginObserver;
    @Mock private StreamObserver<SetupTotpResponse> setupTotpObserver;
    @Mock private StreamObserver<EnableTotpResponse> enableTotpObserver;
    @Mock private StreamObserver<VerifyTotpResponse> verifyTotpObserver;
    @Mock private StreamObserver<DisableTotpResponse> disableTotpObserver;
        @Mock private StreamObserver<AdminDisableTotpResponse> adminDisableTotpObserver;
    @Mock private StreamObserver<RefreshResponse> refreshObserver;
        @Mock private StreamObserver<LogoutResponse> logoutObserver;
        @Mock private StreamObserver<IssueRecoveryTokenResponse> issueRecoveryObserver;
        @Mock private StreamObserver<CompleteRecoveryResponse> completeRecoveryObserver;

    private AuthGrpcService service;
        private Context previousContext;

    private static final String TEST_USER_ID = UUID.randomUUID().toString();
    private static final String TEST_ACCESS_TOKEN = "access.token.here";
    private static final String TEST_REFRESH_TOKEN = "refresh.token.here";

    @BeforeEach
    void setUp() {
        previousContext = Context.current().withValue(
                GrpcAuthInterceptor.PRINCIPAL, UserId.of(TEST_USER_ID))
                .withValue(GrpcAuthInterceptor.PEER_IP, "127.0.0.1")
                .attach();
        service = new AuthGrpcService(
                loginHandler, setupTotpHandler, enableTotpHandler,
                verifyTotpHandler, disableTotpHandler, adminDisableTotpHandler,
                refreshTokenHandler, logoutHandler);
    }

        @AfterEach
        void tearDown() {
                Context.current().detach(previousContext);
        }

    // =========================================================================
    // Constructor
    // =========================================================================

    @Test
    void constructor_ThrowsNPE_WhenLoginHandlerIsNull() {
        assertThrows(NullPointerException.class, () ->
                new AuthGrpcService(null, setupTotpHandler, enableTotpHandler,
                        verifyTotpHandler, disableTotpHandler, adminDisableTotpHandler, refreshTokenHandler, logoutHandler));
    }

    @Test
    void constructor_ThrowsNPE_WhenSetupTotpHandlerIsNull() {
        assertThrows(NullPointerException.class, () ->
                new AuthGrpcService(loginHandler, null, enableTotpHandler,
                        verifyTotpHandler, disableTotpHandler, adminDisableTotpHandler, refreshTokenHandler, logoutHandler));
    }

    @Test
    void constructor_ThrowsNPE_WhenEnableTotpHandlerIsNull() {
        assertThrows(NullPointerException.class, () ->
                new AuthGrpcService(loginHandler, setupTotpHandler, null,
                        verifyTotpHandler, disableTotpHandler, adminDisableTotpHandler, refreshTokenHandler, logoutHandler));
    }

    @Test
    void constructor_ThrowsNPE_WhenVerifyTotpHandlerIsNull() {
        assertThrows(NullPointerException.class, () ->
                new AuthGrpcService(loginHandler, setupTotpHandler, enableTotpHandler,
                        null, disableTotpHandler, adminDisableTotpHandler, refreshTokenHandler, logoutHandler));
    }

    @Test
    void constructor_ThrowsNPE_WhenDisableTotpHandlerIsNull() {
        assertThrows(NullPointerException.class, () ->
                new AuthGrpcService(loginHandler, setupTotpHandler, enableTotpHandler,
                        verifyTotpHandler, null, adminDisableTotpHandler, refreshTokenHandler, logoutHandler));
    }

    @Test
    void constructor_ThrowsNPE_WhenAdminDisableTotpHandlerIsNull() {
        assertThrows(NullPointerException.class, () ->
                new AuthGrpcService(loginHandler, setupTotpHandler, enableTotpHandler,
                        verifyTotpHandler, disableTotpHandler, null, refreshTokenHandler, logoutHandler));
    }

    @Test
    void constructor_ThrowsNPE_WhenRefreshTokenHandlerIsNull() {
        assertThrows(NullPointerException.class, () ->
                new AuthGrpcService(loginHandler, setupTotpHandler, enableTotpHandler,
                        verifyTotpHandler, disableTotpHandler, adminDisableTotpHandler, null, logoutHandler));
    }

    @Test
    void constructor_ThrowsNPE_WhenLogoutHandlerIsNull() {
        assertThrows(NullPointerException.class, () ->
                new AuthGrpcService(loginHandler, setupTotpHandler, enableTotpHandler,
                        verifyTotpHandler, disableTotpHandler, adminDisableTotpHandler, refreshTokenHandler, null));
    }

    // =========================================================================
    // Login
    // =========================================================================

    @Test
    void login_SuccessResult_ReturnsLoginSuccessResponse() {
        final TokenService.TokenPair tokenPair =
                new TokenService.TokenPair(AccessToken.of(TEST_ACCESS_TOKEN), RefreshToken.of(TEST_REFRESH_TOKEN));
        final LoginResult result = LoginResult.success(
                tokenPair, UserId.of(TEST_USER_ID), Set.of(Permission.of("read_data")));

        when(loginHandler.handle(any())).thenReturn(result);

        service.login(validLoginRequest(), loginObserver);

        final ArgumentCaptor<LoginResponse> captor = ArgumentCaptor.forClass(LoginResponse.class);
        verify(loginObserver).onNext(captor.capture());
        verify(loginObserver).onCompleted();

        final LoginResponse response = captor.getValue();
        assertTrue(response.hasSuccess());
        assertEquals(TEST_ACCESS_TOKEN, response.getSuccess().getAccessToken());
        assertEquals(TEST_REFRESH_TOKEN, response.getSuccess().getRefreshToken());
        assertEquals(TEST_USER_ID, response.getSuccess().getUserId());
        assertTrue(response.getSuccess().getPermissionsList().contains("read_data"));
    }

    @Test
    void login_Required2FAResult_ReturnsTotpRequiredResponse() {
        final LoginResult result =
                LoginResult.required2FA(TwoFactorVerificationToken.of("verification.token.here"), UserId.of(TEST_USER_ID));

        when(loginHandler.handle(any())).thenReturn(result);

        service.login(validLoginRequest(), loginObserver);

        final ArgumentCaptor<LoginResponse> captor = ArgumentCaptor.forClass(LoginResponse.class);
        verify(loginObserver).onNext(captor.capture());
        verify(loginObserver).onCompleted();

        final LoginResponse response = captor.getValue();
        assertTrue(response.hasTotpRequired());
        assertEquals("verification.token.here", response.getTotpRequired().getVerificationToken());
        assertEquals(TEST_USER_ID, response.getTotpRequired().getUserId());
    }

    @Test
    void login_PasswordResetRequiredResult_ReturnsPasswordResetResponse() {
        final LoginResult result = LoginResult.passwordResetRequired(UserId.of(TEST_USER_ID));

        when(loginHandler.handle(any())).thenReturn(result);

        service.login(validLoginRequest(), loginObserver);

        final ArgumentCaptor<LoginResponse> captor = ArgumentCaptor.forClass(LoginResponse.class);
        verify(loginObserver).onNext(captor.capture());
        verify(loginObserver).onCompleted();

        final LoginResponse response = captor.getValue();
        assertTrue(response.hasPasswordResetRequired());
        assertEquals(TEST_USER_ID, response.getPasswordResetRequired().getUserId());
    }

        @Test
        void login_MfaEnrollmentRequiredResult_ReturnsEnrollmentResponse() {
                when(loginHandler.handle(any())).thenReturn(LoginResult.mfaEnrollmentRequired(
                                com.oodesigns.cas.domain.value.MfaEnrollmentToken.of("enrollment.token.here"),
                                UserId.of(TEST_USER_ID)));

                service.login(validLoginRequest(), loginObserver);

                final ArgumentCaptor<LoginResponse> captor = ArgumentCaptor.forClass(LoginResponse.class);
                verify(loginObserver).onNext(captor.capture());
                verify(loginObserver).onCompleted();
                assertTrue(captor.getValue().hasMfaEnrollmentRequired());
        }

        @Test
        void login_RejectsMissingPeerIp() {
                final Context previous = Context.current();
                Context.ROOT.attach();
                try {
                        service.login(validLoginRequest(), loginObserver);
                        verifyCanonicalError(loginObserver, Status.Code.INVALID_ARGUMENT);
                } finally {
                        Context.current().detach(previous);
                }
        }

    @Test
    void login_FailureResult_ReturnsErrorResponse() {
        final LoginResult result =
                LoginResult.failure("INVALID_CREDENTIALS", "Invalid username or password");

        when(loginHandler.handle(any())).thenReturn(result);

        service.login(validLoginRequest(), loginObserver);

        verifyCanonicalError(loginObserver, Status.Code.UNAUTHENTICATED);
    }

        @Test
        void login_MapsRemainingApplicationStatusCategories() {
                final String[] codes = {"RATE_LIMITED", "INTERNAL_ERROR", "MFA_SETUP_REQUIRED", "OTHER"};
                final Status.Code[] statuses = {Status.Code.RESOURCE_EXHAUSTED, Status.Code.INTERNAL,
                                Status.Code.FAILED_PRECONDITION, Status.Code.INVALID_ARGUMENT};
                for (int index = 0; index < codes.length; index++) {
                        clearInvocations(loginObserver);
                        when(loginHandler.handle(any())).thenReturn(LoginResult.failure(codes[index], "failure"));
                        service.login(validLoginRequest(), loginObserver);
                        verifyCanonicalError(loginObserver, statuses[index]);
                }
        }

    @Test
    void login_InvalidRequest_ReturnsInvalidRequestError() {
        // Empty username fails Username.of("") validation → IllegalArgumentException
        final LoginRequest invalidRequest = LoginRequest.newBuilder()
                .setUsername("")
                .setPassword("securepassword123")
                .build();

        service.login(invalidRequest, loginObserver);

        verifyCanonicalError(loginObserver, Status.Code.INVALID_ARGUMENT);
        verify(loginHandler, never()).handle(any());
    }

    // =========================================================================
    // Setup TOTP
    // =========================================================================

    @Test
    void setupTotp_SuccessResult_ReturnsSetupTotpSuccessResponse() {
        final SetupTotpResult result = SetupTotpResult.success(
                "JBSWY3DPEHPK3PXP",
                "otpauth://totp/TestIssuer:testuser?secret=JBSWY3DPEHPK3PXP&issuer=TestIssuer");

        when(setupTotpHandler.handle(any())).thenReturn(result);

        service.setupTotp(validSetupTotpRequest(), setupTotpObserver);

        final ArgumentCaptor<SetupTotpResponse> captor =
                ArgumentCaptor.forClass(SetupTotpResponse.class);
        verify(setupTotpObserver).onNext(captor.capture());
        verify(setupTotpObserver).onCompleted();

        final SetupTotpResponse response = captor.getValue();
        assertTrue(response.hasSuccess());
        assertEquals("JBSWY3DPEHPK3PXP", response.getSuccess().getSecret());
    }

        @Test
        void setupTotp_RejectsPrincipalMismatch() {
                service.setupTotp(SetupTotpRequest.newBuilder()
                                .setUserId(UUID.randomUUID().toString()).setUsername("testuser").build(), setupTotpObserver);
                verifyCanonicalError(setupTotpObserver, Status.Code.PERMISSION_DENIED);
        }

    @Test
    void setupTotp_FailureResult_ReturnsErrorResponse() {
        final SetupTotpResult result =
                SetupTotpResult.failure("INTERNAL_ERROR", "Secret generation failed");

        when(setupTotpHandler.handle(any())).thenReturn(result);

        service.setupTotp(validSetupTotpRequest(), setupTotpObserver);

        verifyCanonicalError(setupTotpObserver, Status.Code.INTERNAL);
    }

    @Test
    void setupTotp_InvalidRequest_ReturnsInvalidRequestError() {
        // Non-UUID user_id fails UserId.of() → IllegalArgumentException
        final SetupTotpRequest invalidRequest = SetupTotpRequest.newBuilder()
                .setUserId("not-a-valid-uuid")
                .setUsername("testuser")
                .build();

        service.setupTotp(invalidRequest, setupTotpObserver);

        verifyCanonicalError(setupTotpObserver, Status.Code.INVALID_ARGUMENT);
        verify(setupTotpHandler, never()).handle(any());
    }

    // =========================================================================
    // Enable TOTP
    // =========================================================================

    @Test
    void enableTotp_SuccessResult_ReturnsEnableTotpSuccessResponse() {
        final List<BackupCode> backupCodes = List.of(
                BackupCode.of("ABCD-EFGH-IJKL-MNOP"),
                BackupCode.of("1234-5678-90AB-CDEF"));
        final EnableTotpResult result = EnableTotpResult.success(backupCodes);

        when(enableTotpHandler.handle(any())).thenReturn(result);

        service.enableTotp(validEnableTotpRequest(), enableTotpObserver);

        final ArgumentCaptor<EnableTotpResponse> captor =
                ArgumentCaptor.forClass(EnableTotpResponse.class);
        verify(enableTotpObserver).onNext(captor.capture());
        verify(enableTotpObserver).onCompleted();

        final EnableTotpResponse response = captor.getValue();
        assertTrue(response.hasSuccess());
        assertEquals(2, response.getSuccess().getBackupCodesCount());
        assertTrue(response.getSuccess().getBackupCodesList().contains("ABCD-EFGH-IJKL-MNOP"));
    }

        @Test
        void enableTotp_RejectsPrincipalMismatch() {
                service.enableTotp(EnableTotpRequest.newBuilder()
                                .setUserId(UUID.randomUUID().toString()).setTotpCode("123456").build(), enableTotpObserver);
                verifyCanonicalError(enableTotpObserver, Status.Code.PERMISSION_DENIED);
        }

    @Test
    void enableTotp_FailureResult_ReturnsErrorResponse() {
        final EnableTotpResult result =
                EnableTotpResult.failure("INVALID_TOTP_CODE", "Code did not match");

        when(enableTotpHandler.handle(any())).thenReturn(result);

        service.enableTotp(validEnableTotpRequest(), enableTotpObserver);

        verifyCanonicalError(enableTotpObserver, Status.Code.UNAUTHENTICATED);
    }

    @Test
    void enableTotp_InvalidRequest_ReturnsInvalidRequestError() {
        // Non-UUID user_id fails UserId.of() → IllegalArgumentException
        final EnableTotpRequest invalidRequest = EnableTotpRequest.newBuilder()
                .setUserId("not-a-valid-uuid")
                .setTotpCode("123456")
                .build();

        service.enableTotp(invalidRequest, enableTotpObserver);

        verifyCanonicalError(enableTotpObserver, Status.Code.INVALID_ARGUMENT);
        verify(enableTotpHandler, never()).handle(any());
    }

    // =========================================================================
    // Verify TOTP
    // =========================================================================

    @Test
    void verifyTotp_SuccessResult_ReturnsVerifyTotpSuccessResponse() {
        final TokenService.TokenPair tokenPair =
                new TokenService.TokenPair(AccessToken.of(TEST_ACCESS_TOKEN), RefreshToken.of(TEST_REFRESH_TOKEN));
        final VerifyTotpResult result = VerifyTotpResult.success(
                tokenPair, UserId.of(TEST_USER_ID), Set.of(Permission.of("read_data")));

        when(verifyTotpHandler.handle(any())).thenReturn(result);

        service.verifyTotp(validVerifyTotpRequest(), verifyTotpObserver);

        final ArgumentCaptor<VerifyTotpResponse> captor =
                ArgumentCaptor.forClass(VerifyTotpResponse.class);
        verify(verifyTotpObserver).onNext(captor.capture());
        verify(verifyTotpObserver).onCompleted();

        final VerifyTotpResponse response = captor.getValue();
        assertTrue(response.hasSuccess());
        assertEquals(TEST_ACCESS_TOKEN, response.getSuccess().getAccessToken());
        assertEquals(TEST_REFRESH_TOKEN, response.getSuccess().getRefreshToken());
        assertEquals(TEST_USER_ID, response.getSuccess().getUserId());
        assertTrue(response.getSuccess().getPermissionsList().contains("read_data"));
    }

    @Test
    void verifyTotp_FailureResult_ReturnsErrorResponse() {
        final VerifyTotpResult result =
                VerifyTotpResult.failure("INVALID_TOTP_CODE", "Code mismatch");

        when(verifyTotpHandler.handle(any())).thenReturn(result);

        service.verifyTotp(validVerifyTotpRequest(), verifyTotpObserver);

        verifyCanonicalError(verifyTotpObserver, Status.Code.UNAUTHENTICATED);
    }

    @Test
    void verifyTotp_InvalidRequest_ReturnsInvalidRequestError() {
        // Invalid code format (not 6 digits, not XXXX-XXXX-XXXX-XXXX) fails VerifyTotpCommand
        final VerifyTotpRequest invalidRequest = VerifyTotpRequest.newBuilder()
                .setVerificationToken("some.verification.token")
                .setCode("invalid-code-format")
                .build();

        service.verifyTotp(invalidRequest, verifyTotpObserver);

        verifyCanonicalError(verifyTotpObserver, Status.Code.INVALID_ARGUMENT);
        verify(verifyTotpHandler, never()).handle(any());
    }

    // =========================================================================
    // Refresh (refresh-token rotation)
    // =========================================================================

    @Test
    void refresh_SuccessResult_ReturnsRefreshSuccessResponse() {
        final TokenService.TokenPair tokenPair =
                new TokenService.TokenPair(AccessToken.of(TEST_ACCESS_TOKEN), RefreshToken.of(TEST_REFRESH_TOKEN));
        final RefreshTokenResult result = RefreshTokenResult.success(
                tokenPair, UserId.of(TEST_USER_ID), Set.of(Permission.of("read_data")));

        when(refreshTokenHandler.handle(any())).thenReturn(result);

        service.refresh(validRefreshRequest(), refreshObserver);

        final ArgumentCaptor<RefreshResponse> captor = ArgumentCaptor.forClass(RefreshResponse.class);
        verify(refreshObserver).onNext(captor.capture());
        verify(refreshObserver).onCompleted();

        final RefreshResponse response = captor.getValue();
        assertTrue(response.hasSuccess());
        assertEquals(TEST_ACCESS_TOKEN, response.getSuccess().getAccessToken());
        assertEquals(TEST_REFRESH_TOKEN, response.getSuccess().getRefreshToken());
        assertEquals(TEST_USER_ID, response.getSuccess().getUserId());
        assertTrue(response.getSuccess().getPermissionsList().contains("read_data"));
    }

    @Test
    void refresh_FailureResult_ReturnsErrorResponse() {
        final RefreshTokenResult result =
                RefreshTokenResult.failure("REFRESH_TOKEN_REUSE_DETECTED", "Reuse detected");

        when(refreshTokenHandler.handle(any())).thenReturn(result);

        service.refresh(validRefreshRequest(), refreshObserver);

        verifyCanonicalError(refreshObserver, Status.Code.UNAUTHENTICATED);
    }

    @Test
    void refresh_InvalidRequest_ReturnsInvalidRequestError() {
        // Blank refresh token fails RefreshTokenCommand validation → IllegalArgumentException
        final RefreshRequest invalidRequest = RefreshRequest.newBuilder()
                .setRefreshToken("")
                .build();

        service.refresh(invalidRequest, refreshObserver);

        verifyCanonicalError(refreshObserver, Status.Code.INVALID_ARGUMENT);
        verify(refreshTokenHandler, never()).handle(any());
    }

        // =========================================================================
        // Logout
        // =========================================================================

        @Test
        void logout_SuccessResult_ReturnsLogoutSuccessResponse() {
                when(logoutHandler.handle(any())).thenReturn(LogoutResult.success());

                service.logout(LogoutRequest.newBuilder().setAccessToken(TEST_ACCESS_TOKEN).build(), logoutObserver);

                final ArgumentCaptor<LogoutResponse> captor = ArgumentCaptor.forClass(LogoutResponse.class);
                verify(logoutObserver).onNext(captor.capture());
                verify(logoutObserver).onCompleted();

                final LogoutResponse response = captor.getValue();
                assertTrue(response.hasSuccess());
        }

        @Test
        void disableTotp_RejectsEnrollmentToken() {
                withContext(UserId.of(TEST_USER_ID), Set.of(), true, () ->
                        service.disableTotp(validDisableTotpRequest(), disableTotpObserver));
                verifyCanonicalError(disableTotpObserver, Status.Code.UNAUTHENTICATED);
        }

        @Test
        void disableTotp_RejectsPrincipalMismatch() {
                service.disableTotp(DisableTotpRequest.newBuilder()
                        .setUserId(UUID.randomUUID().toString()).setPassword("securepassword123")
                        .setReason(com.oodesigns.cas.infrastructure.grpc.proto.DisableReason.USER_REQUESTED)
                        .build(), disableTotpObserver);
                verifyCanonicalError(disableTotpObserver, Status.Code.PERMISSION_DENIED);
        }

        @Test
        void disableTotp_AllowsPrivilegedReasonWithCurrentPermission() {
                when(disableTotpHandler.handle(any())).thenReturn(DisableTotpResult.success());
                withContext(UserId.of(TEST_USER_ID), Set.of(Permission.of("manage_mfa")), false, () ->
                        service.disableTotp(DisableTotpRequest.newBuilder().setUserId(TEST_USER_ID)
                                .setPassword("securepassword123")
                                .setReason(com.oodesigns.cas.infrastructure.grpc.proto.DisableReason.ADMIN_FORCED)
                                .build(), disableTotpObserver));
                verify(disableTotpObserver).onCompleted();
        }

        @Test
        void logout_FailureResult_ReturnsErrorResponse() {
                when(logoutHandler.handle(any())).thenReturn(
                                LogoutResult.failure("INVALID_ACCESS_TOKEN", "Invalid token"));

                service.logout(LogoutRequest.newBuilder().setAccessToken(TEST_ACCESS_TOKEN).build(), logoutObserver);

                verifyCanonicalError(logoutObserver, Status.Code.UNAUTHENTICATED);
        }

        @Test
        void logout_InvalidRequest_ReturnsInvalidRequestError() {
                final LogoutRequest invalidRequest = LogoutRequest.newBuilder().setAccessToken("").build();

                service.logout(invalidRequest, logoutObserver);

                verifyCanonicalError(logoutObserver, Status.Code.INVALID_ARGUMENT);
                verify(logoutHandler, never()).handle(any());
        }

    // =========================================================================
    // Disable TOTP
    // =========================================================================

    @Test
    void disableTotp_SuccessResult_ReturnsDisableTotpSuccessResponse() {
        final DisableTotpResult result = DisableTotpResult.success();

        when(disableTotpHandler.handle(any())).thenReturn(result);

        service.disableTotp(validDisableTotpRequest(), disableTotpObserver);

        final ArgumentCaptor<DisableTotpResponse> captor =
                ArgumentCaptor.forClass(DisableTotpResponse.class);
        verify(disableTotpObserver).onNext(captor.capture());
        verify(disableTotpObserver).onCompleted();

        final DisableTotpResponse response = captor.getValue();
        assertTrue(response.hasSuccess());
    }

        @Test
        void logout_RejectsBodyTokenThatDiffersFromBearerToken() {
                final Context previous = Context.current().withValue(
                                GrpcAuthInterceptor.BEARER_TOKEN, "different.token.value").attach();
                try {
                        service.logout(LogoutRequest.newBuilder().setAccessToken(TEST_ACCESS_TOKEN).build(), logoutObserver);
                        verifyCanonicalError(logoutObserver, Status.Code.UNAUTHENTICATED);
                } finally {
                        Context.current().detach(previous);
                }
        }

    @Test
    void disableTotp_FailureResult_ReturnsErrorResponse() {
        final DisableTotpResult result =
                DisableTotpResult.failure("INVALID_PASSWORD", "Password incorrect");

        when(disableTotpHandler.handle(any())).thenReturn(result);

        service.disableTotp(validDisableTotpRequest(), disableTotpObserver);

        verifyCanonicalError(disableTotpObserver, Status.Code.UNAUTHENTICATED);
    }

        @Test
        void disableTotp_RejectsPrivilegedReasonWithoutPermission() {
                final DisableTotpRequest request = DisableTotpRequest.newBuilder()
                                .setUserId(TEST_USER_ID).setPassword("securepassword123")
                                .setReason(com.oodesigns.cas.infrastructure.grpc.proto.DisableReason.ADMIN_FORCED).build();
                service.disableTotp(request, disableTotpObserver);
                verifyCanonicalError(disableTotpObserver, Status.Code.PERMISSION_DENIED);
        }

        @Test
        void disableTotp_RejectsPrivilegedReasonForOwnUserWithoutPermission() {
                final DisableTotpRequest request = DisableTotpRequest.newBuilder()
                                .setUserId(TEST_USER_ID).setPassword("securepassword123")
                                .setReason(com.oodesigns.cas.infrastructure.grpc.proto.DisableReason.ADMIN_FORCED).build();
                service.disableTotp(request, disableTotpObserver);
                verifyCanonicalError(disableTotpObserver, Status.Code.PERMISSION_DENIED);
        }

    @Test
    void disableTotp_UnspecifiedReason_ReturnsInvalidRequestError() {
        // Default proto enum value (0) = DISABLE_REASON_UNSPECIFIED → null → INVALID_REQUEST
        final DisableTotpRequest requestWithNoReason = DisableTotpRequest.newBuilder()
                .setUserId(TEST_USER_ID)
                .setPassword("securepassword123")
                .build();

        service.disableTotp(requestWithNoReason, disableTotpObserver);

        verifyCanonicalError(disableTotpObserver, Status.Code.INVALID_ARGUMENT);
        verify(disableTotpHandler, never()).handle(any());
    }

    @Test
    void disableTotp_InvalidRequest_ReturnsInvalidRequestError() {
        // Non-UUID user_id fails UserId.of() → IllegalArgumentException
        final DisableTotpRequest invalidRequest = DisableTotpRequest.newBuilder()
                .setUserId("not-a-valid-uuid")
                .setPassword("securepassword123")
                .setReason(com.oodesigns.cas.infrastructure.grpc.proto.DisableReason.USER_REQUESTED)
                .build();

        service.disableTotp(invalidRequest, disableTotpObserver);

        verifyCanonicalError(disableTotpObserver, Status.Code.INVALID_ARGUMENT);
        verify(disableTotpHandler, never()).handle(any());
    }

    @Test
        void disableTotp_AdminForcedReason_IsRejectedWithoutAdminAuthorization() {
        final DisableTotpRequest request = DisableTotpRequest.newBuilder()
                .setUserId(TEST_USER_ID)
                .setPassword("securepassword123")
                .setReason(com.oodesigns.cas.infrastructure.grpc.proto.DisableReason.ADMIN_FORCED)
                .build();
        service.disableTotp(request, disableTotpObserver);
        verifyCanonicalError(disableTotpObserver, Status.Code.PERMISSION_DENIED);
        verify(disableTotpHandler, never()).handle(any());
    }

    @Test
        void disableTotp_SecurityIncidentReason_IsRejectedWithoutAdminAuthorization() {
        final DisableTotpRequest request = DisableTotpRequest.newBuilder()
                .setUserId(TEST_USER_ID)
                .setPassword("securepassword123")
                .setReason(com.oodesigns.cas.infrastructure.grpc.proto.DisableReason.SECURITY_INCIDENT)
                .build();
        service.disableTotp(request, disableTotpObserver);
        verifyCanonicalError(disableTotpObserver, Status.Code.PERMISSION_DENIED);
        verify(disableTotpHandler, never()).handle(any());
    }

    @Test
        void disableTotp_RecoveryFlowReason_IsRejectedWithoutAdminAuthorization() {
        final DisableTotpRequest request = DisableTotpRequest.newBuilder()
                .setUserId(TEST_USER_ID)
                .setPassword("securepassword123")
                .setReason(com.oodesigns.cas.infrastructure.grpc.proto.DisableReason.RECOVERY_FLOW)
                .build();
        service.disableTotp(request, disableTotpObserver);
        verifyCanonicalError(disableTotpObserver, Status.Code.PERMISSION_DENIED);
        verify(disableTotpHandler, never()).handle(any());
    }

        // =========================================================================
        // Admin Disable TOTP
        // =========================================================================

        @Test
        void adminDisableTotp_Success_AllowsCrossUserWhenAdminAuthorized() {
                when(adminDisableTotpHandler.handle(any())).thenReturn(DisableTotpResult.success());
                final UserId adminId = UserId.of(UUID.randomUUID());
                final UserId targetId = UserId.of(UUID.randomUUID());

                withContext(adminId, Set.of(Permission.of("manage_mfa")), false, () ->
                        service.adminDisableTotp(validAdminDisableTotpRequest(targetId.asUUID().toString()), adminDisableTotpObserver));

                final ArgumentCaptor<AdminDisableTotpResponse> responseCaptor =
                                ArgumentCaptor.forClass(AdminDisableTotpResponse.class);
                final ArgumentCaptor<com.oodesigns.cas.application.command.AdminDisableTotpCommand> commandCaptor =
                                ArgumentCaptor.forClass(com.oodesigns.cas.application.command.AdminDisableTotpCommand.class);
                verify(adminDisableTotpHandler).handle(commandCaptor.capture());
                verify(adminDisableTotpObserver).onNext(responseCaptor.capture());
                verify(adminDisableTotpObserver).onCompleted();

                final var command = commandCaptor.getValue();
                assertEquals(adminId, command.adminId());
                assertEquals(targetId, command.targetUserId());
                assertEquals(DisableReason.ADMIN_FORCED, command.reason());
                assertEquals("AdminPassword1234", String.valueOf(command.adminPassword().chars()));
                assertTrue(responseCaptor.getValue().hasSuccess());
        }

        @Test
        void adminDisableTotp_RejectsWhenManageMfaPermissionMissing() {
                withContext(UserId.of(UUID.randomUUID()), Set.of(), false, () ->
                        service.adminDisableTotp(validAdminDisableTotpRequest(UUID.randomUUID().toString()), adminDisableTotpObserver));

                final ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
                verify(adminDisableTotpObserver).onError(errorCaptor.capture());
                verify(adminDisableTotpHandler, never()).handle(any());
                assertEquals(Status.PERMISSION_DENIED.getCode(), ((StatusRuntimeException) errorCaptor.getValue()).getStatus().getCode());
        }

        @Test
        void adminDisableTotp_RejectsMissingPrincipal() {
                final Context previous = Context.current();
                Context.ROOT.withValue(
                        GrpcAuthInterceptor.PERMISSIONS, Set.of(Permission.of("manage_mfa"))).attach();
                try {
                        service.adminDisableTotp(validAdminDisableTotpRequest(UUID.randomUUID().toString()), adminDisableTotpObserver);
                        verifyCanonicalError(adminDisableTotpObserver, Status.Code.UNAUTHENTICATED);
                } finally {
                        Context.current().detach(previous);
                }
        }

        @Test
        void adminDisableTotp_RejectsEnrollmentTokenContext() {
                withContext(UserId.of(UUID.randomUUID()), Set.of(Permission.of("manage_mfa")), true, () ->
                        service.adminDisableTotp(validAdminDisableTotpRequest(UUID.randomUUID().toString()), adminDisableTotpObserver));

                final ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
                verify(adminDisableTotpObserver).onError(errorCaptor.capture());
                verify(adminDisableTotpHandler, never()).handle(any());
                assertEquals(Status.UNAUTHENTICATED.getCode(), ((StatusRuntimeException) errorCaptor.getValue()).getStatus().getCode());
        }

        @Test
        void adminDisableTotp_RejectsUserRequestedReason() {
                withContext(UserId.of(UUID.randomUUID()), Set.of(Permission.of("manage_mfa")), false, () ->
                        service.adminDisableTotp(AdminDisableTotpRequest.newBuilder()
                                        .setTargetUserId(UUID.randomUUID().toString())
                                        .setAdminPassword("AdminPassword1234")
                                        .setReason(com.oodesigns.cas.infrastructure.grpc.proto.DisableReason.USER_REQUESTED)
                                        .build(), adminDisableTotpObserver));

                final ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
                verify(adminDisableTotpObserver).onError(errorCaptor.capture());
                verify(adminDisableTotpHandler, never()).handle(any());
                assertEquals(Status.INVALID_ARGUMENT.getCode(), ((StatusRuntimeException) errorCaptor.getValue()).getStatus().getCode());
        }

        @Test
        void adminDisableTotp_RejectsInvalidTargetUserId() {
                withContext(UserId.of(UUID.randomUUID()), Set.of(Permission.of("manage_mfa")), false, () ->
                        service.adminDisableTotp(AdminDisableTotpRequest.newBuilder()
                                        .setTargetUserId("not-a-uuid")
                                        .setAdminPassword("AdminPassword1234")
                                        .setReason(com.oodesigns.cas.infrastructure.grpc.proto.DisableReason.ADMIN_FORCED)
                                        .build(), adminDisableTotpObserver));

                final ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
                verify(adminDisableTotpObserver).onError(errorCaptor.capture());
                verify(adminDisableTotpHandler, never()).handle(any());
                assertEquals(Status.INVALID_ARGUMENT.getCode(), ((StatusRuntimeException) errorCaptor.getValue()).getStatus().getCode());
        }

        @Test
        void adminDisableTotp_RejectsHandlerRuntimeFailure() {
                when(adminDisableTotpHandler.handle(any())).thenThrow(new IllegalStateException("failure"));
                withContext(UserId.of(UUID.randomUUID()), Set.of(Permission.of("manage_mfa")), false, () ->
                        service.adminDisableTotp(validAdminDisableTotpRequest(UUID.randomUUID().toString()), adminDisableTotpObserver));
                verifyCanonicalError(adminDisableTotpObserver, Status.Code.INTERNAL);
        }

    // =========================================================================
    // Helpers
    // =========================================================================

    private LoginRequest validLoginRequest() {
        return LoginRequest.newBuilder()
                .setUsername("testuser")
                .setPassword("securepassword123")
                .build();
    }

    @Test
    void adminDisableTotp_MapsHandlerFailureToCanonicalStatus() {
        when(adminDisableTotpHandler.handle(any())).thenReturn(
                DisableTotpResult.failure("INVALID_PASSWORD", "Administrator reauthentication failed."));
        withContext(UserId.of(UUID.randomUUID()), Set.of(Permission.of("manage_mfa")), false, () ->
                service.adminDisableTotp(validAdminDisableTotpRequest(UUID.randomUUID().toString()), adminDisableTotpObserver));
        verifyCanonicalError(adminDisableTotpObserver, Status.Code.UNAUTHENTICATED);
    }

    @Test
    void recoveryConstructorRejectsNullHandlers() {
        assertThrows(NullPointerException.class, () -> recoveryService(null, completeRecoveryHandler));
        assertThrows(NullPointerException.class, () -> recoveryService(issueRecoveryTokenHandler, null));
    }

    @Test
    void issueRecoveryTokenRejectsEnrollmentAndMissingPermission() {
        final IssueRecoveryTokenRequest request = IssueRecoveryTokenRequest.newBuilder()
                .setTargetUserId(TEST_USER_ID).build();
        withContext(UserId.of(TEST_USER_ID), Set.of(Permission.of("manage_recovery")), true,
                () -> recoveryService(issueRecoveryTokenHandler, completeRecoveryHandler)
                        .issueRecoveryToken(request, issueRecoveryObserver));
        verifyCanonicalError(issueRecoveryObserver, Status.Code.UNAUTHENTICATED);

        clearInvocations(issueRecoveryObserver);
        withContext(UserId.of(TEST_USER_ID), Set.of(), false,
                () -> recoveryService(issueRecoveryTokenHandler, completeRecoveryHandler)
                        .issueRecoveryToken(request, issueRecoveryObserver));
        verifyCanonicalError(issueRecoveryObserver, Status.Code.PERMISSION_DENIED);
    }

    @Test
    void issueRecoveryTokenMapsSuccessAndFailure() {
        final IssueRecoveryTokenRequest request = IssueRecoveryTokenRequest.newBuilder()
                .setTargetUserId(TEST_USER_ID).build();
        when(issueRecoveryTokenHandler.handle(any())).thenReturn(
                IssueRecoveryTokenResult.success(com.oodesigns.cas.domain.value.RecoveryToken.of("recovery.token.value")));
        withContext(UserId.of(TEST_USER_ID), Set.of(Permission.of("manage_recovery")), false,
                () -> recoveryService(issueRecoveryTokenHandler, completeRecoveryHandler)
                        .issueRecoveryToken(request, issueRecoveryObserver));
        verify(issueRecoveryObserver).onNext(any(IssueRecoveryTokenResponse.class));
        verify(issueRecoveryObserver).onCompleted();

        clearInvocations(issueRecoveryObserver);
        when(issueRecoveryTokenHandler.handle(any())).thenReturn(
                IssueRecoveryTokenResult.failure("INTERNAL_ERROR", "failure"));
        withContext(UserId.of(TEST_USER_ID), Set.of(Permission.of("manage_recovery")), false,
                () -> recoveryService(issueRecoveryTokenHandler, completeRecoveryHandler)
                        .issueRecoveryToken(request, issueRecoveryObserver));
        verifyCanonicalError(issueRecoveryObserver, Status.Code.INTERNAL);
    }

    @Test
    void issueRecoveryTokenMapsInvalidRequest() {
        final IssueRecoveryTokenRequest request = IssueRecoveryTokenRequest.newBuilder()
                .setTargetUserId("invalid").build();
        withContext(UserId.of(TEST_USER_ID), Set.of(Permission.of("manage_recovery")), false,
                () -> recoveryService(issueRecoveryTokenHandler, completeRecoveryHandler)
                        .issueRecoveryToken(request, issueRecoveryObserver));
        verifyCanonicalError(issueRecoveryObserver, Status.Code.INVALID_ARGUMENT);
    }

        @Test
        void issueRecoveryTokenRejectsMissingRecoveryHandler() {
                final IssueRecoveryTokenRequest request = IssueRecoveryTokenRequest.newBuilder()
                                .setTargetUserId(TEST_USER_ID).build();
                withContext(UserId.of(TEST_USER_ID), Set.of(Permission.of("manage_recovery")), false,
                                () -> service.issueRecoveryToken(request, issueRecoveryObserver));

                verifyCanonicalError(issueRecoveryObserver, Status.Code.UNAUTHENTICATED);
        }

    @Test
    void completeRecoveryMapsUnavailableSuccessFailureAndInvalidRequest() {
        service.completeRecovery(CompleteRecoveryRequest.newBuilder().build(), completeRecoveryObserver);
        verifyCanonicalError(completeRecoveryObserver, Status.Code.UNAVAILABLE);

        clearInvocations(completeRecoveryObserver);
        when(completeRecoveryHandler.handle(any())).thenReturn(CompleteRecoveryResult.success());
        recoveryService(issueRecoveryTokenHandler, completeRecoveryHandler).completeRecovery(
                CompleteRecoveryRequest.newBuilder().setRecoveryToken("recovery.token.value")
                        .setNewPassword("securepassword123").build(), completeRecoveryObserver);
        verify(completeRecoveryObserver).onNext(any(CompleteRecoveryResponse.class));
        verify(completeRecoveryObserver).onCompleted();

        clearInvocations(completeRecoveryObserver);
        when(completeRecoveryHandler.handle(any())).thenReturn(
                CompleteRecoveryResult.failure("INVALID_RECOVERY_TOKEN", "failure"));
        recoveryService(issueRecoveryTokenHandler, completeRecoveryHandler).completeRecovery(
                CompleteRecoveryRequest.newBuilder().setRecoveryToken("recovery.token.value")
                        .setNewPassword("securepassword123").build(), completeRecoveryObserver);
        verifyCanonicalError(completeRecoveryObserver, Status.Code.UNAUTHENTICATED);

        clearInvocations(completeRecoveryObserver);
        recoveryService(issueRecoveryTokenHandler, completeRecoveryHandler).completeRecovery(
                CompleteRecoveryRequest.newBuilder().setRecoveryToken("bad").setNewPassword("short").build(),
                completeRecoveryObserver);
        verifyCanonicalError(completeRecoveryObserver, Status.Code.INVALID_ARGUMENT);
    }

    private SetupTotpRequest validSetupTotpRequest() {
        return SetupTotpRequest.newBuilder()
                .setUserId(TEST_USER_ID)
                .setUsername("testuser")
                .build();
    }

    private EnableTotpRequest validEnableTotpRequest() {
        return EnableTotpRequest.newBuilder()
                .setUserId(TEST_USER_ID)
                .setTotpCode("123456")
                .build();
    }

    private VerifyTotpRequest validVerifyTotpRequest() {
        return VerifyTotpRequest.newBuilder()
                .setVerificationToken("some.verification.token")
                .setCode("123456")
                .build();
    }

    private DisableTotpRequest validDisableTotpRequest() {
        return DisableTotpRequest.newBuilder()
                .setUserId(TEST_USER_ID)
                .setPassword("securepassword123")
                .setReason(com.oodesigns.cas.infrastructure.grpc.proto.DisableReason.USER_REQUESTED)
                .build();
    }

    private RefreshRequest validRefreshRequest() {
        return RefreshRequest.newBuilder()
                .setRefreshToken("valid.refresh.token")
                .build();
    }

        private AuthGrpcService recoveryService(final IssueRecoveryTokenCommandHandler issueHandler,
                                                                                         final CompleteRecoveryCommandHandler completeHandler) {
                return new AuthGrpcService(loginHandler, setupTotpHandler, enableTotpHandler,
                                verifyTotpHandler, disableTotpHandler, adminDisableTotpHandler,
                                refreshTokenHandler, logoutHandler, issueHandler, completeHandler);
        }

        private AdminDisableTotpRequest validAdminDisableTotpRequest(final String targetUserId) {
                return AdminDisableTotpRequest.newBuilder()
                                .setTargetUserId(targetUserId)
                                .setAdminPassword("AdminPassword1234")
                                .setReason(com.oodesigns.cas.infrastructure.grpc.proto.DisableReason.ADMIN_FORCED)
                                .build();
        }

        private void verifyCanonicalError(final StreamObserver<?> observer, final Status.Code expectedCode) {
                final ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
                verify(observer).onError(errorCaptor.capture());
                assertEquals(expectedCode,
                                ((StatusRuntimeException) errorCaptor.getValue()).getStatus().getCode());
        }

        private void withContext(final UserId principal,
                                                         final Set<Permission> permissions,
                                                         final boolean enrollmentToken,
                                                         final Runnable action) {
                final Context previous = Context.current()
                                .withValue(GrpcAuthInterceptor.PRINCIPAL, principal)
                                .withValue(GrpcAuthInterceptor.PERMISSIONS, permissions)
                                .withValue(GrpcAuthInterceptor.ENROLLMENT_TOKEN, enrollmentToken)
                                .attach();
                try {
                        action.run();
                } finally {
                        Context.current().detach(previous);
                }
        }
}


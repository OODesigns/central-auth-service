package com.oodesigns.cas.infrastructure.grpc;

import com.oodesigns.cas.application.command.DisableTotpCommandHandler;
import com.oodesigns.cas.application.command.DisableTotpResult;
import com.oodesigns.cas.application.command.EnableTotpCommandHandler;
import com.oodesigns.cas.application.command.EnableTotpResult;
import com.oodesigns.cas.application.command.LoginCommandHandler;
import com.oodesigns.cas.application.command.LoginResult;
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
import com.oodesigns.cas.infrastructure.grpc.proto.DisableTotpRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.DisableTotpResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.EnableTotpRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.EnableTotpResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.LoginRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.LoginResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.RefreshRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.RefreshResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.SetupTotpRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.SetupTotpResponse;
import com.oodesigns.cas.infrastructure.grpc.proto.VerifyTotpRequest;
import com.oodesigns.cas.infrastructure.grpc.proto.VerifyTotpResponse;
import io.grpc.stub.StreamObserver;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthGrpcServiceTest {

    @Mock private LoginCommandHandler loginHandler;
    @Mock private SetupTotpCommandHandler setupTotpHandler;
    @Mock private EnableTotpCommandHandler enableTotpHandler;
    @Mock private VerifyTotpCommandHandler verifyTotpHandler;
    @Mock private DisableTotpCommandHandler disableTotpHandler;
    @Mock private RefreshTokenCommandHandler refreshTokenHandler;

    @SuppressWarnings("unchecked")
    @Mock private StreamObserver<LoginResponse> loginObserver;
    @SuppressWarnings("unchecked")
    @Mock private StreamObserver<SetupTotpResponse> setupTotpObserver;
    @SuppressWarnings("unchecked")
    @Mock private StreamObserver<EnableTotpResponse> enableTotpObserver;
    @SuppressWarnings("unchecked")
    @Mock private StreamObserver<VerifyTotpResponse> verifyTotpObserver;
    @SuppressWarnings("unchecked")
    @Mock private StreamObserver<DisableTotpResponse> disableTotpObserver;
    @SuppressWarnings("unchecked")
    @Mock private StreamObserver<RefreshResponse> refreshObserver;

    private AuthGrpcService service;

    private static final String TEST_USER_ID = UUID.randomUUID().toString();
    private static final String TEST_ACCESS_TOKEN = "access.token.here";
    private static final String TEST_REFRESH_TOKEN = "refresh.token.here";

    @BeforeEach
    void setUp() {
        service = new AuthGrpcService(
                loginHandler, setupTotpHandler, enableTotpHandler,
                verifyTotpHandler, disableTotpHandler, refreshTokenHandler);
    }

    // =========================================================================
    // Constructor
    // =========================================================================

    @Test
    void constructor_ThrowsNPE_WhenLoginHandlerIsNull() {
        assertThrows(NullPointerException.class, () ->
                new AuthGrpcService(null, setupTotpHandler, enableTotpHandler,
                        verifyTotpHandler, disableTotpHandler, refreshTokenHandler));
    }

    @Test
    void constructor_ThrowsNPE_WhenSetupTotpHandlerIsNull() {
        assertThrows(NullPointerException.class, () ->
                new AuthGrpcService(loginHandler, null, enableTotpHandler,
                        verifyTotpHandler, disableTotpHandler, refreshTokenHandler));
    }

    @Test
    void constructor_ThrowsNPE_WhenEnableTotpHandlerIsNull() {
        assertThrows(NullPointerException.class, () ->
                new AuthGrpcService(loginHandler, setupTotpHandler, null,
                        verifyTotpHandler, disableTotpHandler, refreshTokenHandler));
    }

    @Test
    void constructor_ThrowsNPE_WhenVerifyTotpHandlerIsNull() {
        assertThrows(NullPointerException.class, () ->
                new AuthGrpcService(loginHandler, setupTotpHandler, enableTotpHandler,
                        null, disableTotpHandler, refreshTokenHandler));
    }

    @Test
    void constructor_ThrowsNPE_WhenDisableTotpHandlerIsNull() {
        assertThrows(NullPointerException.class, () ->
                new AuthGrpcService(loginHandler, setupTotpHandler, enableTotpHandler,
                        verifyTotpHandler, null, refreshTokenHandler));
    }

    @Test
    void constructor_ThrowsNPE_WhenRefreshTokenHandlerIsNull() {
        assertThrows(NullPointerException.class, () ->
                new AuthGrpcService(loginHandler, setupTotpHandler, enableTotpHandler,
                        verifyTotpHandler, disableTotpHandler, null));
    }

    // =========================================================================
    // Login
    // =========================================================================

    @Test
    void login_SuccessResult_ReturnsLoginSuccessResponse() {
        final TokenService.TokenPair tokenPair =
                new TokenService.TokenPair(TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN);
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
                LoginResult.required2FA("verification.token", UserId.of(TEST_USER_ID));

        when(loginHandler.handle(any())).thenReturn(result);

        service.login(validLoginRequest(), loginObserver);

        final ArgumentCaptor<LoginResponse> captor = ArgumentCaptor.forClass(LoginResponse.class);
        verify(loginObserver).onNext(captor.capture());
        verify(loginObserver).onCompleted();

        final LoginResponse response = captor.getValue();
        assertTrue(response.hasTotpRequired());
        assertEquals("verification.token", response.getTotpRequired().getVerificationToken());
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
    void login_FailureResult_ReturnsErrorResponse() {
        final LoginResult result =
                LoginResult.failure("INVALID_CREDENTIALS", "Invalid username or password");

        when(loginHandler.handle(any())).thenReturn(result);

        service.login(validLoginRequest(), loginObserver);

        final ArgumentCaptor<LoginResponse> captor = ArgumentCaptor.forClass(LoginResponse.class);
        verify(loginObserver).onNext(captor.capture());
        verify(loginObserver).onCompleted();

        final LoginResponse response = captor.getValue();
        assertTrue(response.hasError());
        assertEquals("INVALID_CREDENTIALS", response.getError().getErrorCode());
    }

    @Test
    void login_InvalidRequest_ReturnsInvalidRequestError() {
        // Empty username fails Username.of("") validation → IllegalArgumentException
        final LoginRequest invalidRequest = LoginRequest.newBuilder()
                .setUsername("")
                .setPassword("securepassword123")
                .setIpAddress("192.168.1.1")
                .build();

        service.login(invalidRequest, loginObserver);

        final ArgumentCaptor<LoginResponse> captor = ArgumentCaptor.forClass(LoginResponse.class);
        verify(loginObserver).onNext(captor.capture());
        verify(loginObserver).onCompleted();
        verify(loginHandler, never()).handle(any());

        final LoginResponse response = captor.getValue();
        assertTrue(response.hasError());
        assertEquals("INVALID_REQUEST", response.getError().getErrorCode());
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
    void setupTotp_FailureResult_ReturnsErrorResponse() {
        final SetupTotpResult result =
                SetupTotpResult.failure("INTERNAL_ERROR", "Secret generation failed");

        when(setupTotpHandler.handle(any())).thenReturn(result);

        service.setupTotp(validSetupTotpRequest(), setupTotpObserver);

        final ArgumentCaptor<SetupTotpResponse> captor =
                ArgumentCaptor.forClass(SetupTotpResponse.class);
        verify(setupTotpObserver).onNext(captor.capture());
        verify(setupTotpObserver).onCompleted();

        final SetupTotpResponse response = captor.getValue();
        assertTrue(response.hasError());
        assertEquals("INTERNAL_ERROR", response.getError().getErrorCode());
    }

    @Test
    void setupTotp_InvalidRequest_ReturnsInvalidRequestError() {
        // Non-UUID user_id fails UserId.of() → IllegalArgumentException
        final SetupTotpRequest invalidRequest = SetupTotpRequest.newBuilder()
                .setUserId("not-a-valid-uuid")
                .setUsername("testuser")
                .build();

        service.setupTotp(invalidRequest, setupTotpObserver);

        final ArgumentCaptor<SetupTotpResponse> captor =
                ArgumentCaptor.forClass(SetupTotpResponse.class);
        verify(setupTotpObserver).onNext(captor.capture());
        verify(setupTotpObserver).onCompleted();
        verify(setupTotpHandler, never()).handle(any());

        final SetupTotpResponse response = captor.getValue();
        assertTrue(response.hasError());
        assertEquals("INVALID_REQUEST", response.getError().getErrorCode());
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
    void enableTotp_FailureResult_ReturnsErrorResponse() {
        final EnableTotpResult result =
                EnableTotpResult.failure("INVALID_TOTP_CODE", "Code did not match");

        when(enableTotpHandler.handle(any())).thenReturn(result);

        service.enableTotp(validEnableTotpRequest(), enableTotpObserver);

        final ArgumentCaptor<EnableTotpResponse> captor =
                ArgumentCaptor.forClass(EnableTotpResponse.class);
        verify(enableTotpObserver).onNext(captor.capture());
        verify(enableTotpObserver).onCompleted();

        final EnableTotpResponse response = captor.getValue();
        assertTrue(response.hasError());
        assertEquals("INVALID_TOTP_CODE", response.getError().getErrorCode());
    }

    @Test
    void enableTotp_InvalidRequest_ReturnsInvalidRequestError() {
        // Non-UUID user_id fails UserId.of() → IllegalArgumentException
        final EnableTotpRequest invalidRequest = EnableTotpRequest.newBuilder()
                .setUserId("not-a-valid-uuid")
                .setTotpCode("123456")
                .build();

        service.enableTotp(invalidRequest, enableTotpObserver);

        final ArgumentCaptor<EnableTotpResponse> captor =
                ArgumentCaptor.forClass(EnableTotpResponse.class);
        verify(enableTotpObserver).onNext(captor.capture());
        verify(enableTotpObserver).onCompleted();
        verify(enableTotpHandler, never()).handle(any());

        final EnableTotpResponse response = captor.getValue();
        assertTrue(response.hasError());
        assertEquals("INVALID_REQUEST", response.getError().getErrorCode());
    }

    // =========================================================================
    // Verify TOTP
    // =========================================================================

    @Test
    void verifyTotp_SuccessResult_ReturnsVerifyTotpSuccessResponse() {
        final TokenService.TokenPair tokenPair =
                new TokenService.TokenPair(TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN);
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

        final ArgumentCaptor<VerifyTotpResponse> captor =
                ArgumentCaptor.forClass(VerifyTotpResponse.class);
        verify(verifyTotpObserver).onNext(captor.capture());
        verify(verifyTotpObserver).onCompleted();

        final VerifyTotpResponse response = captor.getValue();
        assertTrue(response.hasError());
        assertEquals("INVALID_TOTP_CODE", response.getError().getErrorCode());
    }

    @Test
    void verifyTotp_InvalidRequest_ReturnsInvalidRequestError() {
        // Invalid code format (not 6 digits, not XXXX-XXXX-XXXX-XXXX) fails VerifyTotpCommand
        final VerifyTotpRequest invalidRequest = VerifyTotpRequest.newBuilder()
                .setVerificationToken("some.verification.token")
                .setCode("invalid-code-format")
                .build();

        service.verifyTotp(invalidRequest, verifyTotpObserver);

        final ArgumentCaptor<VerifyTotpResponse> captor =
                ArgumentCaptor.forClass(VerifyTotpResponse.class);
        verify(verifyTotpObserver).onNext(captor.capture());
        verify(verifyTotpObserver).onCompleted();
        verify(verifyTotpHandler, never()).handle(any());

        final VerifyTotpResponse response = captor.getValue();
        assertTrue(response.hasError());
        assertEquals("INVALID_REQUEST", response.getError().getErrorCode());
    }

    // =========================================================================
    // Refresh (refresh-token rotation)
    // =========================================================================

    @Test
    void refresh_SuccessResult_ReturnsRefreshSuccessResponse() {
        final TokenService.TokenPair tokenPair =
                new TokenService.TokenPair(TEST_ACCESS_TOKEN, TEST_REFRESH_TOKEN);
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

        final ArgumentCaptor<RefreshResponse> captor = ArgumentCaptor.forClass(RefreshResponse.class);
        verify(refreshObserver).onNext(captor.capture());
        verify(refreshObserver).onCompleted();

        final RefreshResponse response = captor.getValue();
        assertTrue(response.hasError());
        assertEquals("REFRESH_TOKEN_REUSE_DETECTED", response.getError().getErrorCode());
    }

    @Test
    void refresh_InvalidRequest_ReturnsInvalidRequestError() {
        // Blank refresh token fails RefreshTokenCommand validation → IllegalArgumentException
        final RefreshRequest invalidRequest = RefreshRequest.newBuilder()
                .setRefreshToken("")
                .build();

        service.refresh(invalidRequest, refreshObserver);

        final ArgumentCaptor<RefreshResponse> captor = ArgumentCaptor.forClass(RefreshResponse.class);
        verify(refreshObserver).onNext(captor.capture());
        verify(refreshObserver).onCompleted();
        verify(refreshTokenHandler, never()).handle(any());

        final RefreshResponse response = captor.getValue();
        assertTrue(response.hasError());
        assertEquals("INVALID_REQUEST", response.getError().getErrorCode());
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
    void disableTotp_FailureResult_ReturnsErrorResponse() {
        final DisableTotpResult result =
                DisableTotpResult.failure("INVALID_PASSWORD", "Password incorrect");

        when(disableTotpHandler.handle(any())).thenReturn(result);

        service.disableTotp(validDisableTotpRequest(), disableTotpObserver);

        final ArgumentCaptor<DisableTotpResponse> captor =
                ArgumentCaptor.forClass(DisableTotpResponse.class);
        verify(disableTotpObserver).onNext(captor.capture());
        verify(disableTotpObserver).onCompleted();

        final DisableTotpResponse response = captor.getValue();
        assertTrue(response.hasError());
        assertEquals("INVALID_PASSWORD", response.getError().getErrorCode());
    }

    @Test
    void disableTotp_UnspecifiedReason_ReturnsInvalidRequestError() {
        // Default proto enum value (0) = DISABLE_REASON_UNSPECIFIED → null → INVALID_REQUEST
        final DisableTotpRequest requestWithNoReason = DisableTotpRequest.newBuilder()
                .setUserId(TEST_USER_ID)
                .setPassword("securepassword123")
                .build();

        service.disableTotp(requestWithNoReason, disableTotpObserver);

        final ArgumentCaptor<DisableTotpResponse> captor =
                ArgumentCaptor.forClass(DisableTotpResponse.class);
        verify(disableTotpObserver).onNext(captor.capture());
        verify(disableTotpObserver).onCompleted();
        verify(disableTotpHandler, never()).handle(any());

        final DisableTotpResponse response = captor.getValue();
        assertTrue(response.hasError());
        assertEquals("INVALID_REQUEST", response.getError().getErrorCode());
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

        final ArgumentCaptor<DisableTotpResponse> captor =
                ArgumentCaptor.forClass(DisableTotpResponse.class);
        verify(disableTotpObserver).onNext(captor.capture());
        verify(disableTotpObserver).onCompleted();
        verify(disableTotpHandler, never()).handle(any());

        final DisableTotpResponse response = captor.getValue();
        assertTrue(response.hasError());
        assertEquals("INVALID_REQUEST", response.getError().getErrorCode());
    }

    @Test
    void disableTotp_AdminForcedReason_IsAccepted() {
        when(disableTotpHandler.handle(any())).thenReturn(DisableTotpResult.success());
        final DisableTotpRequest request = DisableTotpRequest.newBuilder()
                .setUserId(TEST_USER_ID)
                .setPassword("securepassword123")
                .setReason(com.oodesigns.cas.infrastructure.grpc.proto.DisableReason.ADMIN_FORCED)
                .build();
        service.disableTotp(request, disableTotpObserver);
        final ArgumentCaptor<DisableTotpResponse> captor =
                ArgumentCaptor.forClass(DisableTotpResponse.class);
        verify(disableTotpObserver).onNext(captor.capture());
        verify(disableTotpObserver).onCompleted();
        assertTrue(captor.getValue().hasSuccess());
    }

    @Test
    void disableTotp_SecurityIncidentReason_IsAccepted() {
        when(disableTotpHandler.handle(any())).thenReturn(DisableTotpResult.success());
        final DisableTotpRequest request = DisableTotpRequest.newBuilder()
                .setUserId(TEST_USER_ID)
                .setPassword("securepassword123")
                .setReason(com.oodesigns.cas.infrastructure.grpc.proto.DisableReason.SECURITY_INCIDENT)
                .build();
        service.disableTotp(request, disableTotpObserver);
        final ArgumentCaptor<DisableTotpResponse> captor =
                ArgumentCaptor.forClass(DisableTotpResponse.class);
        verify(disableTotpObserver).onNext(captor.capture());
        verify(disableTotpObserver).onCompleted();
        assertTrue(captor.getValue().hasSuccess());
    }

    @Test
    void disableTotp_RecoveryFlowReason_IsAccepted() {
        when(disableTotpHandler.handle(any())).thenReturn(DisableTotpResult.success());
        final DisableTotpRequest request = DisableTotpRequest.newBuilder()
                .setUserId(TEST_USER_ID)
                .setPassword("securepassword123")
                .setReason(com.oodesigns.cas.infrastructure.grpc.proto.DisableReason.RECOVERY_FLOW)
                .build();
        service.disableTotp(request, disableTotpObserver);
        final ArgumentCaptor<DisableTotpResponse> captor =
                ArgumentCaptor.forClass(DisableTotpResponse.class);
        verify(disableTotpObserver).onNext(captor.capture());
        verify(disableTotpObserver).onCompleted();
        assertTrue(captor.getValue().hasSuccess());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private LoginRequest validLoginRequest() {
        return LoginRequest.newBuilder()
                .setUsername("testuser")
                .setPassword("securepassword123")
                .setIpAddress("192.168.1.1")
                .build();
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
}


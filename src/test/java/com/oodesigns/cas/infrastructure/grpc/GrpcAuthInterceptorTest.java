package com.oodesigns.cas.infrastructure.grpc;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.AccessToken;
import com.oodesigns.cas.domain.value.MfaEnrollmentToken;
import com.oodesigns.cas.domain.value.Jti;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.infrastructure.grpc.proto.AuthServiceGrpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@SuppressWarnings("unchecked")
class GrpcAuthInterceptorTest {
    private static final String TOKEN = "access.token.value";
    private static final String ENROLLMENT_TOKEN = "enrollment.token.value";
    private static final UserId USER_ID = UserId.of("00000000-0000-0000-0000-000000000001");
    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of(
            "authorization", Metadata.ASCII_STRING_MARSHALLER);

    @Test
    void allowsPublicRpcWithoutBearerToken() {
        final Ports.TokenVerifier verifier = mock(Ports.TokenVerifier.class);
        final ServerCall<Object, Object> call = call(AuthServiceGrpc.getLoginMethod());
        final ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);

        final Metadata headers = new Metadata();
        new GrpcAuthInterceptor(verifier).interceptCall(call, headers, next);
        verify(next).startCall(call, headers);
        verify(verifier, never()).verifyAccessToken(AccessToken.of(TOKEN));
    }

    @Test
    void rejectsMissingBearerTokenForProtectedRpc() {
        final Ports.TokenVerifier verifier = mock(Ports.TokenVerifier.class);
        final ServerCall<Object, Object> call = call(AuthServiceGrpc.getDisableTotpMethod());
        final ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);

        new GrpcAuthInterceptor(verifier).interceptCall(call, new Metadata(), next);

        verify(call).close(any(Status.class), any(Metadata.class));
        verify(next, never()).startCall(any(), any());
    }

    @Test
    void allowsAccessTokenForDisable() {
        final Ports.TokenVerifier verifier = mock(Ports.TokenVerifier.class);
        when(verifier.verifyAccessToken(AccessToken.of(TOKEN))).thenReturn(Optional.of(claims()));
        final ServerCall<Object, Object> call = call(AuthServiceGrpc.getDisableTotpMethod());
        final ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);
        final Metadata headers = headers("Bearer " + TOKEN);

        new GrpcAuthInterceptor(verifier).interceptCall(call, headers, next);

        verify(next).startCall(call, headers);
    }


    @Test
    void allowsEnrollmentTokenForSetup() {
        final Ports.TokenVerifier verifier = mock(Ports.TokenVerifier.class);
        when(verifier.verifyAccessToken(AccessToken.of(ENROLLMENT_TOKEN))).thenReturn(Optional.empty());
        when(verifier.verifyMfaEnrollmentToken(MfaEnrollmentToken.of(ENROLLMENT_TOKEN))).thenReturn(Optional.of(USER_ID));
        final ServerCall<Object, Object> call = call(AuthServiceGrpc.getSetupTotpMethod());
        final ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);

        final Metadata requestHeaders = headers("Bearer " + ENROLLMENT_TOKEN);
        new GrpcAuthInterceptor(verifier).interceptCall(call, requestHeaders, next);

        verify(next).startCall(call, requestHeaders);
    }

    @Test
    void rejectsEnrollmentTokenForDisable() {
        final Ports.TokenVerifier verifier = mock(Ports.TokenVerifier.class);
        when(verifier.verifyAccessToken(AccessToken.of(ENROLLMENT_TOKEN))).thenReturn(Optional.empty());
        final ServerCall<Object, Object> call = call(AuthServiceGrpc.getDisableTotpMethod());
        final ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);

        new GrpcAuthInterceptor(verifier).interceptCall(call, headers("Bearer " + ENROLLMENT_TOKEN), next);

        verify(call).close(any(Status.class), any(Metadata.class));
    }

    private ServerCall<Object, Object> call(final MethodDescriptor<?, ?> method) {
        final ServerCall<Object, Object> call = mock(ServerCall.class);
        when(call.getMethodDescriptor()).thenReturn((MethodDescriptor<Object, Object>) method);
        return call;
    }

    private Metadata headers(final String value) {
        final Metadata headers = new Metadata();
        headers.put(AUTHORIZATION, value);
        return headers;
    }

    private Ports.AccessTokenClaims claims() {
        return new Ports.AccessTokenClaims(USER_ID, Jti.generate(), Instant.now().plusSeconds(60));
    }
}

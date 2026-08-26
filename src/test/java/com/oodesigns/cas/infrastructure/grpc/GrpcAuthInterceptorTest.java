package com.oodesigns.cas.infrastructure.grpc;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.entity.User;
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
import java.util.Set;
import javax.net.ssl.SSLSession;
import java.security.cert.X509Certificate;

import io.grpc.Attributes;
import io.grpc.Grpc;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings({"unchecked", "rawtypes"})
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
        new GrpcAuthInterceptor(verifier, mock(Ports.UserRetriever.class)).interceptCall(call, headers, next);
        verify(next).startCall(call, headers);
        verify(verifier, never()).verifyAccessToken(AccessToken.of(TOKEN));
    }

    @Test
    void rejectsMissingBearerTokenForProtectedRpc() {
        final Ports.TokenVerifier verifier = mock(Ports.TokenVerifier.class);
        final ServerCall<Object, Object> call = call(AuthServiceGrpc.getDisableTotpMethod());
        final ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);

        new GrpcAuthInterceptor(verifier, mock(Ports.UserRetriever.class)).interceptCall(call, new Metadata(), next);

        final org.mockito.ArgumentCaptor<Status> statusCaptor =
            org.mockito.ArgumentCaptor.forClass(Status.class);
        verify(call).close(statusCaptor.capture(), any(Metadata.class));
        assertEquals(Status.Code.UNAUTHENTICATED, statusCaptor.getValue().getCode());
        verify(next, never()).startCall(any(), any());
    }

    @Test
    void rejectsMalformedBearerTokenAsUnauthenticated() {
        final Ports.TokenVerifier verifier = mock(Ports.TokenVerifier.class);
        final ServerCall<Object, Object> call = call(AuthServiceGrpc.getDisableTotpMethod());
        final ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);

        new GrpcAuthInterceptor(verifier, mock(Ports.UserRetriever.class)).interceptCall(call, headers("Bearer malformed"), next);

        verify(call).close(any(Status.class), any(Metadata.class));
        verify(next, never()).startCall(any(), any());
    }

    @Test
    void allowsAccessTokenForDisable() {
        final Ports.TokenVerifier verifier = mock(Ports.TokenVerifier.class);
        final Ports.UserRetriever userRetriever = mock(Ports.UserRetriever.class);
        when(verifier.verifyAccessToken(AccessToken.of(TOKEN))).thenReturn(Optional.of(claims()));
        final User currentUser = user(Set.of());
        when(userRetriever.findById(USER_ID)).thenReturn(Optional.of(currentUser));
        final ServerCall<Object, Object> call = call(AuthServiceGrpc.getDisableTotpMethod());
        final ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);
        final Metadata headers = headers("Bearer " + TOKEN);

        new GrpcAuthInterceptor(verifier, userRetriever).interceptCall(call, headers, next);

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
        new GrpcAuthInterceptor(verifier, mock(Ports.UserRetriever.class)).interceptCall(call, requestHeaders, next);

        verify(next).startCall(call, requestHeaders);
    }

    @Test
    void rejectsEnrollmentTokenForDisable() {
        final Ports.TokenVerifier verifier = mock(Ports.TokenVerifier.class);
        when(verifier.verifyAccessToken(AccessToken.of(ENROLLMENT_TOKEN))).thenReturn(Optional.empty());
        final ServerCall<Object, Object> call = call(AuthServiceGrpc.getDisableTotpMethod());
        final ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);

        new GrpcAuthInterceptor(verifier, mock(Ports.UserRetriever.class)).interceptCall(call, headers("Bearer " + ENROLLMENT_TOKEN), next);

        verify(call).close(any(Status.class), any(Metadata.class));
    }

    @Test
    void usesCurrentPermissionsForAdministrativeRpc() {
        final Ports.TokenVerifier verifier = mock(Ports.TokenVerifier.class);
        final Ports.UserRetriever userRetriever = mock(Ports.UserRetriever.class);
        when(verifier.verifyAccessToken(AccessToken.of(TOKEN))).thenReturn(Optional.of(claimsWithPermission()));
        final User currentUser = user(Set.of());
        when(userRetriever.findById(USER_ID)).thenReturn(Optional.of(currentUser));
        final ServerCall<Object, Object> call = call(AuthServiceGrpc.getAdminDisableTotpMethod());
        final ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);
        final Metadata headers = headers("Bearer " + TOKEN);

        new GrpcAuthInterceptor(verifier, userRetriever).interceptCall(call, headers, next);

        verify(next).startCall(call, headers);
        verify(userRetriever).findById(USER_ID);
    }

    @Test
    void rejectsAdministrativeRpcWhenCurrentPermissionsUnavailable() {
        final Ports.TokenVerifier verifier = mock(Ports.TokenVerifier.class);
        final Ports.UserRetriever userRetriever = mock(Ports.UserRetriever.class);
        when(verifier.verifyAccessToken(AccessToken.of(TOKEN))).thenReturn(Optional.of(claimsWithPermission()));
        when(userRetriever.findById(USER_ID)).thenThrow(new IllegalStateException("database unavailable"));
        final ServerCall<Object, Object> call = call(AuthServiceGrpc.getAdminDisableTotpMethod());
        final ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);

        new GrpcAuthInterceptor(verifier, userRetriever).interceptCall(call, headers("Bearer " + TOKEN), next);

        final org.mockito.ArgumentCaptor<Status> statusCaptor =
                org.mockito.ArgumentCaptor.forClass(Status.class);
        verify(call).close(statusCaptor.capture(), any(Metadata.class));
        assertEquals(Status.Code.UNAVAILABLE, statusCaptor.getValue().getCode());
        verify(next, never()).startCall(any(), any());
    }

    @Test
    void requiresTrustedClientRetrieverWhenMachineClientsAreRequired() {
        assertThrows(NullPointerException.class,
                () -> new GrpcAuthInterceptor(mock(Ports.TokenVerifier.class),
                        mock(Ports.UserRetriever.class), null, true));
    }

    @Test
    void rejectsMachineClientCallWithoutTlsSession() {
        final Ports.TokenVerifier verifier = mock(Ports.TokenVerifier.class);
        final Ports.TrustedClientRetriever clients = mock(Ports.TrustedClientRetriever.class);
        final ServerCall<Object, Object> call = call(AuthServiceGrpc.getLoginMethod());
        final ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);

        new GrpcAuthInterceptor(verifier, mock(Ports.UserRetriever.class), clients, true)
                .interceptCall(call, new Metadata(), next);

        verify(call).close(any(Status.class), any(Metadata.class));
        verify(next, never()).startCall(any(), any());
    }

    @Test
    void machineClientLookupHandlesNullAttributesAndUnsupportedCertificate() throws Exception {
        final Ports.TrustedClientRetriever clients = mock(Ports.TrustedClientRetriever.class);
        final ServerCall<Object, Object> nullAttributesCall = mock(ServerCall.class);
        when(nullAttributesCall.getMethodDescriptor()).thenReturn((MethodDescriptor) AuthServiceGrpc.getLoginMethod());
        final ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);
        new GrpcAuthInterceptor(mock(Ports.TokenVerifier.class), mock(Ports.UserRetriever.class), clients, true)
                .interceptCall(nullAttributesCall, new Metadata(), next);
        verify(nullAttributesCall).close(any(Status.class), any(Metadata.class));

        final SSLSession session = mock(SSLSession.class);
        when(session.getPeerCertificates()).thenReturn(new java.security.cert.Certificate[] {mock(java.security.cert.Certificate.class)});
        final ServerCall<Object, Object> unsupportedCall = call(AuthServiceGrpc.getLoginMethod(),
                Attributes.newBuilder().set(Grpc.TRANSPORT_ATTR_SSL_SESSION, session).build());
        new GrpcAuthInterceptor(mock(Ports.TokenVerifier.class), mock(Ports.UserRetriever.class), clients, true)
                .interceptCall(unsupportedCall, new Metadata(), next);
        verify(unsupportedCall).close(any(Status.class), any(Metadata.class));
    }

    @Test
    void exposesMachineClientContextValue() {
        assertEquals(null, GrpcAuthInterceptor.machineClientId());
    }

    private ServerCall<Object, Object> call(final MethodDescriptor<?, ?> method) {
        return call(method, Attributes.EMPTY);
    }

    private ServerCall<Object, Object> call(final MethodDescriptor<?, ?> method, final Attributes attributes) {
        final ServerCall<Object, Object> call = mock(ServerCall.class);
        when(call.getMethodDescriptor()).thenReturn((MethodDescriptor<Object, Object>) method);
        when(call.getAttributes()).thenReturn(attributes);
        return call;
    }

    private Metadata headers(final String value) {
        final Metadata headers = new Metadata();
        headers.put(AUTHORIZATION, value);
        return headers;
    }
    @Test
    void acceptsRegisteredMachineClientCertificate() throws Exception {
        final Ports.TokenVerifier verifier = mock(Ports.TokenVerifier.class);
        final Ports.TrustedClientRetriever clients = mock(Ports.TrustedClientRetriever.class);
        final SSLSession session = mock(SSLSession.class);
        final X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getEncoded()).thenReturn(new byte[] {1, 2, 3});
        when(session.getPeerCertificates()).thenReturn(new java.security.cert.Certificate[] {certificate});
        final Attributes attributes = Attributes.newBuilder()
                .set(Grpc.TRANSPORT_ATTR_SSL_SESSION, session).build();
        final String fingerprint = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(new byte[] {1, 2, 3}));
        when(clients.findByFingerprint(fingerprint)).thenReturn(Optional.of(
                new Ports.TrustedClient(java.util.UUID.randomUUID(), fingerprint,
                        Instant.now().plusSeconds(60), null)));
        final ServerCall<Object, Object> call = call(AuthServiceGrpc.getLoginMethod(), attributes);
        final ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);

        new GrpcAuthInterceptor(verifier, mock(Ports.UserRetriever.class), clients, true)
                .interceptCall(call, new Metadata(), next);

        verify(next).startCall(any(), any());
    }

        @Test
        void bindsMachineClientToProtectedContext() throws Exception {
        final Ports.TokenVerifier verifier = mock(Ports.TokenVerifier.class);
        final Ports.TrustedClientRetriever clients = mock(Ports.TrustedClientRetriever.class);
        when(verifier.verifyAccessToken(AccessToken.of(TOKEN))).thenReturn(Optional.of(claims()));
        final Ports.UserRetriever users = mock(Ports.UserRetriever.class);
        final User currentUser = user(Set.of());
        when(users.findById(USER_ID)).thenReturn(Optional.of(currentUser));
        final SSLSession session = mock(SSLSession.class);
        final X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getEncoded()).thenReturn(new byte[] {4, 5, 6});
        when(session.getPeerCertificates()).thenReturn(new java.security.cert.Certificate[] {certificate});
        final String fingerprint = java.util.HexFormat.of().formatHex(
            java.security.MessageDigest.getInstance("SHA-256").digest(new byte[] {4, 5, 6}));
        final java.util.UUID machineId = java.util.UUID.randomUUID();
        when(clients.findByFingerprint(fingerprint)).thenReturn(Optional.of(
            new Ports.TrustedClient(machineId, fingerprint, Instant.now().plusSeconds(60), null)));
        final ServerCall<Object, Object> call = call(AuthServiceGrpc.getDisableTotpMethod(),
            Attributes.newBuilder().set(Grpc.TRANSPORT_ATTR_SSL_SESSION, session).build());
        final ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);

        new GrpcAuthInterceptor(verifier, users, clients, true)
            .interceptCall(call, headers("Bearer " + TOKEN), next);

        verify(next).startCall(any(), any());
        }

        @Test
        void certificateLookupExceptionsFailClosed() throws Exception {
        final Ports.TrustedClientRetriever clients = mock(Ports.TrustedClientRetriever.class);
        final SSLSession session = mock(SSLSession.class);
        when(session.getPeerCertificates()).thenThrow(new RuntimeException("certificate error"));
        final ServerCall<Object, Object> call = call(AuthServiceGrpc.getLoginMethod(),
            Attributes.newBuilder().set(Grpc.TRANSPORT_ATTR_SSL_SESSION, session).build());
        final ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);

        new GrpcAuthInterceptor(mock(Ports.TokenVerifier.class), mock(Ports.UserRetriever.class), clients, true)
            .interceptCall(call, new Metadata(), next);

        verify(call).close(any(Status.class), any(Metadata.class));
        verify(next, never()).startCall(any(), any());
        }

        @Test
        void peerIpReturnsNullForUnsupportedAddress() {
        final ServerCall<Object, Object> call = mock(ServerCall.class);
        when(call.getAttributes()).thenReturn(Attributes.newBuilder()
            .set(Grpc.TRANSPORT_ATTR_REMOTE_ADDR, new java.net.SocketAddress() {}).build());
        assertEquals(null, GrpcAuthInterceptor.peerIp(call));
        }

    @Test
    void peerIpReturnsNullWhenAttributesAreMissing() {
        final ServerCall<Object, Object> call = mock(ServerCall.class);
        when(call.getAttributes()).thenReturn(null);
        assertEquals(null, GrpcAuthInterceptor.peerIp(call));
    }

    @Test
    void rejectsAdministrativeCallWhenUserPermissionReadFails() {
        final Ports.TokenVerifier verifier = mock(Ports.TokenVerifier.class);
        final Ports.UserRetriever users = mock(Ports.UserRetriever.class);
        when(verifier.verifyAccessToken(AccessToken.of(TOKEN))).thenReturn(Optional.of(claimsWithPermission()));
        final User currentUser = mock(User.class);
        when(currentUser.permissions()).thenThrow(new IllegalStateException("permission read failed"));
        when(users.findById(USER_ID)).thenReturn(Optional.of(currentUser));
        final ServerCall<Object, Object> call = call(AuthServiceGrpc.getAdminDisableTotpMethod());
        final ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);

        new GrpcAuthInterceptor(verifier, users).interceptCall(call, headers("Bearer " + TOKEN), next);

        verify(call).close(any(Status.class), any(Metadata.class));
        verify(next, never()).startCall(any(), any());
    }

    private Ports.AccessTokenClaims claims() {
        return new Ports.AccessTokenClaims(USER_ID, Jti.generate(), Instant.now().plusSeconds(60));
    }

    private Ports.AccessTokenClaims claimsWithPermission() {
        return new Ports.AccessTokenClaims(USER_ID, Jti.generate(), Instant.now().plusSeconds(60),
                Set.of(com.oodesigns.cas.domain.value.Permission.of("manage_mfa")));
    }

    private User user(final Set<com.oodesigns.cas.domain.value.Permission> permissions) {
        final User user = mock(User.class);
        when(user.permissions()).thenReturn(permissions);
        return user;
    }
}

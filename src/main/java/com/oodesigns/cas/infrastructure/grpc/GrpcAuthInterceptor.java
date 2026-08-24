package com.oodesigns.cas.infrastructure.grpc;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.UserId;
import com.oodesigns.cas.domain.value.Permission;
import com.oodesigns.cas.domain.value.AccessToken;
import com.oodesigns.cas.domain.value.MfaEnrollmentToken;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.ServerCall.Listener;

import java.util.Objects;
import java.util.Optional;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/** Verifies bearer tokens and binds protected RPCs to a request-scoped principal. */
public final class GrpcAuthInterceptor implements ServerInterceptor {
    static final Context.Key<UserId> PRINCIPAL = Context.key("cas-principal");
    static final Context.Key<String> BEARER_TOKEN = Context.key("cas-bearer-token");
    static final Context.Key<Boolean> ENROLLMENT_TOKEN = Context.key("cas-enrollment-token");
    static final Context.Key<java.util.Set<Permission>> PERMISSIONS = Context.key("cas-permissions");
    static final Context.Key<String> PEER_IP = Context.key("cas-peer-ip");
    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of(
            "authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final Ports.TokenVerifier tokenVerifier;

    public GrpcAuthInterceptor(final Ports.TokenVerifier tokenVerifier) {
        this.tokenVerifier = Objects.requireNonNull(tokenVerifier, "TokenVerifier is required");
    }

    public static UserId principal() {
        return PRINCIPAL.get();
    }

    public static String bearerToken() {
        return BEARER_TOKEN.get();
    }

    public static boolean isEnrollmentToken() {
        return Boolean.TRUE.equals(ENROLLMENT_TOKEN.get());
    }

    public static boolean hasPermission(final String permission) {
        final java.util.Set<Permission> permissions = PERMISSIONS.get();
        return permissions != null && permission != null
                && permissions.stream().anyMatch(value -> value.value().equals(permission));
    }

    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(
            final ServerCall<ReqT, RespT> call,
            final Metadata headers,
            final ServerCallHandler<ReqT, RespT> next) {
        final String method = call.getMethodDescriptor().getFullMethodName();
        if (isPublic(method)) {
            final Context context = Context.current().withValue(PEER_IP, peerIp(call));
            return Contexts.interceptCall(context, call, headers, next);
        }

        final String bearer = headers.get(AUTHORIZATION);
        if (bearer == null || !bearer.startsWith("Bearer ") || bearer.length() <= 7) {
            call.close(Status.UNAUTHENTICATED.withDescription("Bearer access token is required"), new Metadata());
            return new Listener<>() {};
        }

        final String token = bearer.substring(7).trim();
        final AccessToken accessToken = AccessToken.of(token);
        final Optional<Ports.AccessTokenClaims> accessClaims = tokenVerifier.verifyAccessToken(accessToken);
        final Optional<UserId> accessPrincipal = accessClaims.map(claims -> claims.userId());
        final boolean enrollmentMethod = isSetupOrEnable(method);
        final Optional<UserId> principal = accessPrincipal.isPresent()
                ? accessPrincipal
                : enrollmentMethod ? tokenVerifier.verifyMfaEnrollmentToken(MfaEnrollmentToken.of(token)) : Optional.empty();
        if (principal.isEmpty() || (!enrollmentMethod && accessPrincipal.isEmpty())) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid or unauthorized bearer token"), new Metadata());
            return new Listener<>() {};
        }

        final Context context = Context.current()
                .withValue(PRINCIPAL, principal.orElseThrow())
                .withValue(BEARER_TOKEN, token)
                .withValue(ENROLLMENT_TOKEN, accessPrincipal.isEmpty())
                .withValue(PERMISSIONS, accessClaims.map(claims -> claims.permissions()).orElse(java.util.Set.of()))
                .withValue(PEER_IP, peerIp(call));
        return Contexts.interceptCall(context, call, headers, next);
    }

    static String peerIp(final ServerCall<?, ?> call) {
        final io.grpc.Attributes attributes = call.getAttributes();
        if (attributes == null) {
            return null;
        }
        final SocketAddress address = attributes.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
        return address instanceof InetSocketAddress socketAddress
            ? socketAddress.getAddress().getHostAddress()
            : null;
    }

    private boolean isPublic(final String method) {
        return method.endsWith("/Login") || method.endsWith("/Refresh") || method.endsWith("/VerifyTotp");
    }

    private boolean isSetupOrEnable(final String method) {
        return method.endsWith("/SetupTotp") || method.endsWith("/EnableTotp");
    }
}
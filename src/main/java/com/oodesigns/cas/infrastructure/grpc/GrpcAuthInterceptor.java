package com.oodesigns.cas.infrastructure.grpc;

import com.oodesigns.cas.domain.service.Ports;
import com.oodesigns.cas.domain.value.UserId;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.ServerCall.Listener;

import java.util.Objects;
import java.util.Optional;

/** Verifies bearer tokens and binds protected RPCs to a request-scoped principal. */
public final class GrpcAuthInterceptor implements ServerInterceptor {
    static final Context.Key<UserId> PRINCIPAL = Context.key("cas-principal");
    static final Context.Key<String> BEARER_TOKEN = Context.key("cas-bearer-token");
    static final Context.Key<Boolean> ENROLLMENT_TOKEN = Context.key("cas-enrollment-token");
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

    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(
            final ServerCall<ReqT, RespT> call,
            final Metadata headers,
            final ServerCallHandler<ReqT, RespT> next) {
        final String method = call.getMethodDescriptor().getFullMethodName();
        if (isPublic(method)) {
            return next.startCall(call, headers);
        }

        final String bearer = headers.get(AUTHORIZATION);
        if (bearer == null || !bearer.startsWith("Bearer ") || bearer.length() <= 7) {
            call.close(Status.UNAUTHENTICATED.withDescription("Bearer access token is required"), new Metadata());
            return new Listener<>() {};
        }

        final String token = bearer.substring(7).trim();
        final Optional<UserId> accessPrincipal = tokenVerifier.verifyAccessToken(token)
            .map(claims -> claims.userId());
        final boolean enrollmentMethod = isSetupOrEnable(method);
        final Optional<UserId> principal = accessPrincipal.isPresent()
                ? accessPrincipal
                : enrollmentMethod ? tokenVerifier.verifyMfaEnrollmentToken(token) : Optional.empty();
        if (principal.isEmpty() || (!enrollmentMethod && accessPrincipal.isEmpty())) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid or unauthorized bearer token"), new Metadata());
            return new Listener<>() {};
        }

        final Context context = Context.current()
                .withValue(PRINCIPAL, principal.orElseThrow())
                .withValue(BEARER_TOKEN, token)
                .withValue(ENROLLMENT_TOKEN, accessPrincipal.isEmpty());
        return Contexts.interceptCall(context, call, headers, next);
    }

    private boolean isPublic(final String method) {
        return method.endsWith("/Login") || method.endsWith("/Refresh") || method.endsWith("/VerifyTotp");
    }

    private boolean isSetupOrEnable(final String method) {
        return method.endsWith("/SetupTotp") || method.endsWith("/EnableTotp");
    }
}
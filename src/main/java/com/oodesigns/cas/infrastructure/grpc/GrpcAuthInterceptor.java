package com.oodesigns.cas.infrastructure.grpc;

import com.google.protobuf.Any;
import com.google.rpc.ErrorInfo;
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
import io.grpc.protobuf.StatusProto;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import javax.net.ssl.SSLSession;

/** Verifies bearer tokens and binds protected RPCs to a request-scoped principal. */
public final class GrpcAuthInterceptor implements ServerInterceptor {
    static final Context.Key<UserId> PRINCIPAL = Context.key("cas-principal");
    static final Context.Key<String> BEARER_TOKEN = Context.key("cas-bearer-token");
    static final Context.Key<Boolean> ENROLLMENT_TOKEN = Context.key("cas-enrollment-token");
    static final Context.Key<java.util.Set<Permission>> PERMISSIONS = Context.key("cas-permissions");
    static final Context.Key<String> PEER_IP = Context.key("cas-peer-ip");
    static final Context.Key<java.util.UUID> MACHINE_CLIENT_ID = Context.key("cas-machine-client-id");
    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of(
            "authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final Ports.TokenVerifier tokenVerifier;
    private final Ports.UserRetriever userRetriever;
    private final Ports.TrustedClientRetriever trustedClientRetriever;
    private final boolean requireMachineClient;

    public GrpcAuthInterceptor(final Ports.TokenVerifier tokenVerifier,
                               final Ports.UserRetriever userRetriever) {
        this(tokenVerifier, userRetriever, null, false);
    }

    public GrpcAuthInterceptor(final Ports.TokenVerifier tokenVerifier,
                               final Ports.UserRetriever userRetriever,
                               final Ports.TrustedClientRetriever trustedClientRetriever,
                               final boolean requireMachineClient) {
        this.tokenVerifier = Objects.requireNonNull(tokenVerifier, "TokenVerifier is required");
        this.userRetriever = Objects.requireNonNull(userRetriever, "UserRetriever is required");
        this.requireMachineClient = requireMachineClient;
        this.trustedClientRetriever = requireMachineClient
                ? Objects.requireNonNull(trustedClientRetriever, "TrustedClientRetriever is required")
                : trustedClientRetriever;
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
        final Optional<java.util.UUID> machineClient = resolveMachineClient(call);
        if (requireMachineClient && machineClient.isEmpty()) {
            reject(call, Status.Code.UNAUTHENTICATED, "MTLS_REJECTED",
                    "Trusted machine client certificate is required");
            return new Listener<>() {};
        }
        if (isPublic(method) || isReflection(method)) {
            Context context = Context.current().withValue(PEER_IP, peerIp(call));
            if (machineClient.isPresent()) {
                context = context.withValue(MACHINE_CLIENT_ID, machineClient.orElseThrow());
            }
            return Contexts.interceptCall(context, call, headers, next);
        }

        final String bearer = headers.get(AUTHORIZATION);
        if (bearer == null || !bearer.startsWith("Bearer ") || bearer.length() <= 7) {
            reject(call, Status.Code.UNAUTHENTICATED, "UNAUTHENTICATED", "Bearer access token is required");
            return new Listener<>() {};
        }

        final String token = bearer.substring(7).trim();
        final AccessToken accessToken;
        try {
            accessToken = AccessToken.of(token);
        } catch (final IllegalArgumentException exception) {
            reject(call, Status.Code.UNAUTHENTICATED, "UNAUTHENTICATED", "Invalid bearer token");
            return new Listener<>() {};
        }
        final Optional<Ports.AccessTokenClaims> accessClaims = tokenVerifier.verifyAccessToken(accessToken);
        final Optional<UserId> accessPrincipal = accessClaims.map(claims -> claims.userId());
        final boolean enrollmentMethod = isSetupOrEnable(method);
        final Optional<UserId> principal;
        if (accessPrincipal.isPresent()) {
            principal = accessPrincipal;
        } else if (enrollmentMethod) {
            principal = tokenVerifier.verifyMfaEnrollmentToken(MfaEnrollmentToken.of(token));
        } else {
            principal = Optional.empty();
        }
        if (principal.isEmpty()) {
            reject(call, Status.Code.UNAUTHENTICATED, "UNAUTHENTICATED", "Invalid or unauthorized bearer token");
            return new Listener<>() {};
        }

        final Set<Permission> permissions;
        try {
            permissions = currentPermissions(method, accessPrincipal, accessClaims);
        } catch (final RuntimeException exception) {
            reject(call, Status.Code.UNAVAILABLE, "AUTHORIZATION_UNAVAILABLE", "Authorization state is unavailable");
            return new Listener<>() {};
        }
        Context context = Context.current()
                .withValue(PRINCIPAL, principal.orElseThrow())
                .withValue(BEARER_TOKEN, token)
                .withValue(ENROLLMENT_TOKEN, accessPrincipal.isEmpty())
                .withValue(PERMISSIONS, permissions)
                .withValue(PEER_IP, peerIp(call));
        if (machineClient.isPresent()) {
            context = context.withValue(MACHINE_CLIENT_ID, machineClient.orElseThrow());
        }
        return Contexts.interceptCall(context, call, headers, next);
    }

    public static java.util.UUID machineClientId() {
        return MACHINE_CLIENT_ID.get();
    }

    private Optional<java.util.UUID> resolveMachineClient(final ServerCall<?, ?> call) {
        if (!requireMachineClient) {
            return Optional.empty();
        }
        try {
            final io.grpc.Attributes attributes = call.getAttributes();
            final SSLSession session = attributes == null
                    ? null : attributes.get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
            if (session == null) {
                return Optional.empty();
            }
            final Certificate[] certificates = session.getPeerCertificates();
            if (certificates.length == 0 || !(certificates[0] instanceof X509Certificate certificate)) {
                return Optional.empty();
            }
            final String fingerprint = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
            return trustedClientRetriever.findByFingerprint(fingerprint)
                    .filter(client -> client.isActive(java.time.Instant.now()))
                    .map(client -> client.id());
        } catch (final Exception exception) {
            return Optional.empty();
        }
    }

    private Set<Permission> currentPermissions(
            final String method,
            final Optional<UserId> accessPrincipal,
            final Optional<Ports.AccessTokenClaims> accessClaims) {
        if (!isAuthorizationSensitive(method)) {
            return accessClaims.map(claims -> claims.permissions()).orElse(Set.of());
        }
        try {
            return accessPrincipal.flatMap(userRetriever::findById)
                    .map(user -> user.permissions())
                    .orElseThrow(() -> new IllegalStateException("Authenticated user is unavailable"));
        } catch (final RuntimeException exception) {
            throw new IllegalStateException("Authorization state is unavailable", exception);
        }
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
        return method.endsWith("/Login") || method.endsWith("/Refresh")
            || method.endsWith("/VerifyTotp") || method.endsWith("/CompleteRecovery");
    }

    private boolean isSetupOrEnable(final String method) {
        return method.endsWith("/SetupTotp") || method.endsWith("/EnableTotp");
    }

    private boolean isAuthorizationSensitive(final String method) {
        return method.endsWith("/DisableTotp") || method.endsWith("/AdminDisableTotp")
            || method.endsWith("/IssueRecoveryToken");
    }

    private boolean isReflection(final String method) {
        return method.startsWith("grpc.");
    }

    private void reject(final ServerCall<?, ?> call, final Status.Code code,
                        final String reason, final String message) {
        final com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
            .setCode(code.value())
            .setMessage(message)
            .addDetails(Any.pack(ErrorInfo.newBuilder()
                .setReason(reason)
                .setDomain("central-auth-service")
                .build()))
            .build();
        final io.grpc.StatusRuntimeException exception = StatusProto.toStatusRuntimeException(status);
        call.close(exception.getStatus(), exception.getTrailers());
    }
}
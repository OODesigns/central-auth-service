package com.oodesigns.cas.infrastructure.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/** Emits bounded, structured security events without reading credential-bearing requests. */
public final class GrpcSecurityEventInterceptor implements ServerInterceptor {
    static final Context.Key<String> CORRELATION_ID = Context.key("cas-correlation-id");
    private static final Metadata.Key<String> CORRELATION_ID_HEADER = Metadata.Key.of(
            "x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Logger LOGGER = Logger.getLogger(GrpcSecurityEventInterceptor.class.getName());

    private final String environment;

    public GrpcSecurityEventInterceptor(final String environment) {
        this.environment = Objects.requireNonNull(environment, "Environment is required");
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            final ServerCall<ReqT, RespT> call,
            final Metadata headers,
            final ServerCallHandler<ReqT, RespT> next) {
        final String correlationId = correlationId(headers.get(CORRELATION_ID_HEADER));
        final String method = call.getMethodDescriptor().getFullMethodName();
        final ServerCall<ReqT, RespT> loggedCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void close(final Status status, final Metadata trailers) {
                log(method, correlationId, status);
                super.close(status, trailers);
            }
        };
        return Contexts.interceptCall(Context.current().withValue(CORRELATION_ID, correlationId),
                loggedCall, headers, next);
    }

    static String correlationId(final String supplied) {
        if (supplied != null && supplied.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }

    private void log(final String method, final String correlationId, final Status status) {
        LOGGER.info(() -> "{\"event\":\"" + eventName(method, status)
                + "\",\"rpc_method\":\"" + method
                + "\",\"grpc_status\":\"" + status.getCode().name()
                + "\",\"result_category\":\"" + (status.isOk() ? "success" : "failure")
                + "\",\"environment\":\"" + environment
                + "\",\"correlation_id\":\"" + correlationId + "\"}");
    }

    private String eventName(final String method, final Status status) {
        if (status.isOk()) {
            return "security_request_completed";
        }
        if (status.getCode() == Status.Code.UNAUTHENTICATED) {
            return method.endsWith("/VerifyTotp") ? "mfa_failure" : "auth_failure";
        }
        if (status.getCode() == Status.Code.PERMISSION_DENIED) {
            return "authz_denied";
        }
        if (status.getCode() == Status.Code.RESOURCE_EXHAUSTED) {
            return "rate_limit_exceeded";
        }
        return "security_request_failed";
    }
}
package com.oodesigns.cas.infrastructure.grpc;

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.Contexts;
import io.grpc.Context;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Records bounded gRPC and authentication metrics without credential identifiers. */
@SuppressWarnings("null")
public final class GrpcMetricsInterceptor implements ServerInterceptor {
    private static final AttributeKey<String> RPC_METHOD = AttributeKey.stringKey("rpc_method");
    private static final AttributeKey<String> STATUS_CODE = AttributeKey.stringKey("grpc_status_code");
    private static final AttributeKey<String> RESULT_CATEGORY = AttributeKey.stringKey("result_category");
    private static final AttributeKey<String> ENVIRONMENT = AttributeKey.stringKey("environment");

    private final String environment;
    private LongCounter callCount;
    private DoubleHistogram callDuration;
    private LongCounter callErrors;
    private LongCounter deadlineExceeded;
    private LongCounter loginSuccess;
    private LongCounter loginFailure;
    private LongCounter rateLimit;
    private LongCounter totpFailure;
    private LongCounter mtlsRejection;
    private LongCounter adminAction;
    private LongCounter authorizationLookupFailure;

    public GrpcMetricsInterceptor(final String environment) {
        this.environment = Objects.requireNonNull(environment, "Environment is required");
        final Meter meter = GlobalOpenTelemetry.getMeter("central-auth-service");
        initializeMetrics(meter);
    }

    public GrpcMetricsInterceptor(final io.opentelemetry.api.metrics.MeterProvider meterProvider,
                                  final String environment) {
        this.environment = Objects.requireNonNull(environment, "Environment is required");
        initializeMetrics(Objects.requireNonNull(meterProvider, "MeterProvider is required")
                .get("central-auth-service"));
    }

    private void initializeMetrics(final Meter meter) {
        callCount = meter.counterBuilder("grpc_server_call_count").setDescription("Completed gRPC server calls").build();
        callDuration = meter.histogramBuilder("grpc_server_call_duration").setUnit("s").setDescription("gRPC server call duration").build();
        callErrors = meter.counterBuilder("grpc_server_call_errors").setDescription("gRPC server errors").build();
        deadlineExceeded = meter.counterBuilder("grpc_server_deadline_exceeded").setDescription("gRPC calls exceeding deadlines").build();
        loginSuccess = meter.counterBuilder("auth_login_success").setDescription("Successful logins").build();
        loginFailure = meter.counterBuilder("auth_login_failure").setDescription("Failed logins").build();
        rateLimit = meter.counterBuilder("auth_rate_limit").setDescription("Rate-limited authentication calls").build();
        totpFailure = meter.counterBuilder("auth_totp_failure").setDescription("Failed TOTP calls").build();
        mtlsRejection = meter.counterBuilder("auth_mtls_rejection").setDescription("Rejected machine-client certificates").build();
        adminAction = meter.counterBuilder("auth_admin_action").setDescription("Administrative authentication actions").build();
        authorizationLookupFailure = meter.counterBuilder("auth_authorization_lookup_failure").setDescription("Authorization lookup failures").build();
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            final ServerCall<ReqT, RespT> call,
            final Metadata headers,
            final ServerCallHandler<ReqT, RespT> next) {
        final String method = call.getMethodDescriptor().getFullMethodName();
        final long started = System.nanoTime();
        final ServerCall<ReqT, RespT> monitoredCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void close(final Status status, final Metadata trailers) {
                record(method, status, System.nanoTime() - started);
                super.close(status, trailers);
            }
        };
        final Context context = Context.current();
        return Contexts.interceptCall(context, monitoredCall, headers, next);
    }

    private void record(final String method, final Status status, final long durationNanos) {
        final Attributes attributes = Attributes.of(
                RPC_METHOD, method,
                STATUS_CODE, status.getCode().name(),
                RESULT_CATEGORY, status.isOk() ? "success" : "failure",
                ENVIRONMENT, environment);
        callCount.add(1, attributes);
        callDuration.record(durationNanos / (double) TimeUnit.SECONDS.toNanos(1), attributes);
        if (!status.isOk()) {
            callErrors.add(1, attributes);
        }
        if (status.getCode() == Status.Code.DEADLINE_EXCEEDED) {
            deadlineExceeded.add(1, attributes);
        }
        if (method.endsWith("/Login")) {
            (status.isOk() ? loginSuccess : loginFailure).add(1, attributes);
        }
        if (status.getCode() == Status.Code.RESOURCE_EXHAUSTED) {
            rateLimit.add(1, attributes);
        }
        if (method.endsWith("/VerifyTotp") && status.getCode() == Status.Code.UNAUTHENTICATED) {
            totpFailure.add(1, attributes);
        }
        final String statusDescription = status.getDescription();
        if (statusDescription != null && statusDescription.contains("machine client certificate")) {
            mtlsRejection.add(1, attributes);
        }
        if (method.endsWith("/AdminDisableTotp") && status.isOk()) {
            adminAction.add(1, attributes);
        }
        if (status.getCode() == Status.Code.UNAVAILABLE
                && (method.endsWith("/DisableTotp") || method.endsWith("/AdminDisableTotp"))) {
            authorizationLookupFailure.add(1, attributes);
        }
    }
}
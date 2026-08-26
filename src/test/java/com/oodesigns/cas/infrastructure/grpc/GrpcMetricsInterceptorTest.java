package com.oodesigns.cas.infrastructure.grpc;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "null"})
class GrpcMetricsInterceptorTest {

        @Test
        void supportsGlobalMeterConstructor() {
                new GrpcMetricsInterceptor("test");
        }

    @Test
    void recordsRequestedGrpcAndAuthenticationMetricPaths() {
        try (SdkMeterProvider meterProvider = SdkMeterProvider.builder().build()) {
            final GrpcMetricsInterceptor interceptor =
                    new GrpcMetricsInterceptor(meterProvider, "test");

            close(interceptor, com.oodesigns.cas.infrastructure.grpc.proto.AuthServiceGrpc.getLoginMethod(),
                    Status.OK);
            close(interceptor, com.oodesigns.cas.infrastructure.grpc.proto.AuthServiceGrpc.getLoginMethod(),
                    Status.UNAUTHENTICATED);
            close(interceptor, com.oodesigns.cas.infrastructure.grpc.proto.AuthServiceGrpc.getVerifyTotpMethod(),
                    Status.UNAUTHENTICATED);
            close(interceptor, com.oodesigns.cas.infrastructure.grpc.proto.AuthServiceGrpc.getAdminDisableTotpMethod(),
                    Status.OK);
            close(interceptor, com.oodesigns.cas.infrastructure.grpc.proto.AuthServiceGrpc.getAdminDisableTotpMethod(),
                    Status.UNAVAILABLE);
            close(interceptor, com.oodesigns.cas.infrastructure.grpc.proto.AuthServiceGrpc.getDisableTotpMethod(),
                    Status.UNAVAILABLE);
            close(interceptor, com.oodesigns.cas.infrastructure.grpc.proto.AuthServiceGrpc.getLoginMethod(),
                    Status.RESOURCE_EXHAUSTED);
            close(interceptor, com.oodesigns.cas.infrastructure.grpc.proto.AuthServiceGrpc.getLoginMethod(),
                    Status.DEADLINE_EXCEEDED);
            close(interceptor, com.oodesigns.cas.infrastructure.grpc.proto.AuthServiceGrpc.getLoginMethod(),
                    Status.UNAUTHENTICATED.withDescription("Trusted machine client certificate is required"));
        }
    }

    private <ReqT, RespT> void close(
            final GrpcMetricsInterceptor interceptor,
            final MethodDescriptor<ReqT, RespT> method,
            final Status status) {
        final ServerCall<ReqT, RespT> call = mock(ServerCall.class);
        final ServerCallHandler<ReqT, RespT> next = mock(ServerCallHandler.class);
        when(call.getMethodDescriptor()).thenReturn(method);
        when(next.startCall(any(), any())).thenReturn(new ServerCall.Listener<>() {});

        interceptor.interceptCall(call, new Metadata(), next);
        final var monitoredCall = org.mockito.ArgumentCaptor.forClass(ServerCall.class);
        verify(next).startCall(monitoredCall.capture(), any());
        final ServerCall<ReqT, RespT> captured = (ServerCall<ReqT, RespT>) monitoredCall.getValue();
        captured.close(status, new Metadata());
    }
}
package com.oodesigns.cas.infrastructure.grpc;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class GrpcSecurityEventInterceptorTest {
    @Mock private ServerCall<String, String> call;
    @Mock private ServerCallHandler<String, String> next;
    @Mock private ServerCall.Listener<String> listener;
    private MethodDescriptor<String, String> method;

    @BeforeEach
    void setUp() {
        method = mock(MethodDescriptor.class);
        lenient().when(method.getFullMethodName()).thenReturn("cas.v1.AuthService/Login");
        lenient().when(call.getMethodDescriptor()).thenReturn(method);
        lenient().when(next.startCall(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(listener);
    }

    @Test
    void rejectsNullEnvironment() {
        assertThrows(NullPointerException.class, () -> new GrpcSecurityEventInterceptor(null));
    }

    @Test
    void interceptsCallAndLogsSecurityOutcomes() {
        final GrpcSecurityEventInterceptor interceptor = new GrpcSecurityEventInterceptor("test");
        final Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER), "request-42");

        interceptor.interceptCall(call, headers, next);

        final ArgumentCaptor<ServerCall<String, String>> captor = ArgumentCaptor.forClass(ServerCall.class);
        verify(next).startCall(captor.capture(), org.mockito.ArgumentMatchers.same(headers));
        final ServerCall<String, String> loggedCall = captor.getValue();
        loggedCall.close(Status.OK, new Metadata());
        loggedCall.close(Status.UNAUTHENTICATED, new Metadata());
        loggedCall.close(Status.PERMISSION_DENIED, new Metadata());
        loggedCall.close(Status.RESOURCE_EXHAUSTED, new Metadata());
        loggedCall.close(Status.INVALID_ARGUMENT, new Metadata());
        verify(call, org.mockito.Mockito.times(5)).close(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void usesMfaEventNameForTotpFailures() {
        when(method.getFullMethodName()).thenReturn("cas.v1.AuthService/VerifyTotp");
        final GrpcSecurityEventInterceptor interceptor = new GrpcSecurityEventInterceptor("test");

        interceptor.interceptCall(call, new Metadata(), next);

        final ArgumentCaptor<ServerCall<String, String>> captor = ArgumentCaptor.forClass(ServerCall.class);
        verify(next).startCall(captor.capture(), org.mockito.ArgumentMatchers.any());
        captor.getValue().close(Status.UNAUTHENTICATED, new Metadata());
    }

    @Test
    void acceptsSafeCorrelationId() {
        assertEquals("request-42", GrpcSecurityEventInterceptor.correlationId("request-42"));
    }

    @Test
    void replacesUnsafeCorrelationId() {
        final String correlationId = GrpcSecurityEventInterceptor.correlationId("bad\nvalue");

        assertNotEquals("bad\nvalue", correlationId);
        assertEquals(36, correlationId.length());
    }
}
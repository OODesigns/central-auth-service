package com.oodesigns.cas.infrastructure.grpc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SuppressWarnings("unchecked")
class GrpcSecurityEventInterceptorTest {
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
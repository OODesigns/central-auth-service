package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Payload Value Object Tests")
class PayloadTest {

    @Test
    @DisplayName("Should create payload when value is non-blank")
    void shouldCreatePayload() {
        final Payload payload = Payload.of("{\"sub\":\"user\"}");
        assertEquals("{\"sub\":\"user\"}", payload.value());
    }

    @Test
    @DisplayName("Should throw NullPointerException for null payload")
    void shouldThrowForNullPayload() {
        assertThrows(NullPointerException.class, () -> Payload.of(null));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for blank payload")
    void shouldThrowForBlankPayload() {
        assertThrows(IllegalArgumentException.class, () -> Payload.of("   "));
    }
}

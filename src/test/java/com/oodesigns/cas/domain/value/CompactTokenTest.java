package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompactTokenTest {
    @Test
    void rejectsBlankAndMalformedTokens() {
        assertThrows(NullPointerException.class, () -> AccessToken.of(null));
        assertThrows(IllegalArgumentException.class, () -> AccessToken.of("   "));
        assertThrows(IllegalArgumentException.class, () -> AccessToken.of("one.two"));
        assertThrows(IllegalArgumentException.class, () -> AccessToken.of("one..three"));
    }

    @Test
    void masksTokenWhenDisplayed() {
        assertEquals("AccessToken{***}", AccessToken.of("one.two.three").toString());
    }
}

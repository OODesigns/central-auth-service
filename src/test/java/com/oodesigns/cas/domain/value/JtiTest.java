package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Jti (JWT ID) value object.
 * Validates: generation, parsing, immutability.
 */
class JtiTest {

    @Test
    void testGenerateCreatesValidUUID() {
        Jti jti = Jti.generate();
        assertNotNull(jti);
        assertNotNull(jti.asUUID());
        assertNotNull(jti.asString());
    }

    @Test
    void testFromUUID() {
        UUID uuid = UUID.randomUUID();
        Jti jti = new Jti(uuid);
        assertEquals(uuid, jti.asUUID());
    }

    @Test
    void testFromString() {
        String uuidStr = UUID.randomUUID().toString();
        Jti jti = Jti.of(uuidStr);
        assertEquals(uuidStr, jti.asString());
    }

    @Test
    void testFromInvalidStringThrows() {
        assertThrows(IllegalArgumentException.class, () -> Jti.of("not-a-uuid"));
    }

    @Test
    void testEqualityBasedOnUUID() {
        UUID uuid = UUID.randomUUID();
        Jti jti1 = new Jti(uuid);
        Jti jti2 = new Jti(uuid);
        assertEquals(jti1, jti2);
    }

    @Test
    void testInequalityDifferentUUIDs() {
        Jti jti1 = Jti.generate();
        Jti jti2 = Jti.generate();
        assertNotEquals(jti1, jti2);
    }

    @Test
    void testHashCodeConsistency() {
        UUID uuid = UUID.randomUUID();
        Jti jti1 = new Jti(uuid);
        Jti jti2 = new Jti(uuid);
        assertEquals(jti1.hashCode(), jti2.hashCode());
    }

    @Test
    void testNullFromUUIDThrows() {
        assertThrows(NullPointerException.class, () -> new Jti(null));
    }

    @Test
    void testNullFromStringThrows() {
        assertThrows(NullPointerException.class, () -> Jti.of(null));
    }
}

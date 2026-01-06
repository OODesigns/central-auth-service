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
        final Jti jti = Jti.generate();
        assertNotNull(jti);
        assertNotNull(jti.asUUID());
        assertNotNull(jti.toString());
    }

    @Test
    void testFromUUID() {
        final UUID uuid = UUID.randomUUID();
        final Jti jti = new Jti(uuid);
        assertEquals(uuid, jti.asUUID());
    }

    @Test
    void testFromString() {
        final String uuidStr = UUID.randomUUID().toString();
        final Jti jti = Jti.of(uuidStr);
        assertEquals(uuidStr, jti.toString());
    }

    @Test
    void testFromInvalidStringThrows() {
        assertThrows(IllegalArgumentException.class, () -> Jti.of("not-a-uuid"));
    }

    @Test
    void testEqualityBasedOnUUID() {
        final UUID uuid = UUID.randomUUID();
        final Jti jti1 = new Jti(uuid);
        final Jti jti2 = new Jti(uuid);
        assertEquals(jti1, jti2);
    }

    @Test
    void testInequalityDifferentUUIDs() {
        final Jti jti1 = Jti.generate();
        final Jti jti2 = Jti.generate();
        assertNotEquals(jti1, jti2);
    }

    @Test
    void testHashCodeConsistency() {
        final UUID uuid = UUID.randomUUID();
        final Jti jti1 = new Jti(uuid);
        final Jti jti2 = new Jti(uuid);
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

    @Test
    void testToStringReturnsUUIDString() {
        final UUID uuid = UUID.randomUUID();
        final Jti jti = new Jti(uuid);
        assertEquals(uuid.toString(), jti.toString());
    }
}

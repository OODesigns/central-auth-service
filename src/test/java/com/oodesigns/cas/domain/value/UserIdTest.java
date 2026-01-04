package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserId value object.
 * Validates: generation, parsing, immutability, equality, hashCode.
 */
class UserIdTest {

    @Test
    void testGenerateCreatesValidUUID() {
        UserId id = UserId.generate();
        assertNotNull(id);
        assertNotNull(id.asUUID());
        assertNotNull(id.toString());
    }

    @Test
    void testFromUUID() {
        UUID uuid = UUID.randomUUID();
        UserId id = new UserId(uuid);
        assertEquals(uuid, id.asUUID());
    }

    @Test
    void testFromString() {
        String uuidStr = UUID.randomUUID().toString();
        UserId id = UserId.of(uuidStr);
        assertEquals(uuidStr, id.toString());
    }

    @Test
    void testFromInvalidStringThrows() {
        assertThrows(IllegalArgumentException.class, () -> UserId.of("not-a-uuid"));
    }

    @Test
    void testEqualityBasedOnUUID() {
        UUID uuid = UUID.randomUUID();
        UserId id1 = new UserId(uuid);
        UserId id2 = new UserId(uuid);
        assertEquals(id1, id2);
    }

    @Test
    void testInequalityDifferentUUIDs() {
        UserId id1 = UserId.generate();
        UserId id2 = UserId.generate();
        assertNotEquals(id1, id2);
    }

    @Test
    void testHashCodeConsistency() {
        UUID uuid = UUID.randomUUID();
        UserId id1 = new UserId(uuid);
        UserId id2 = new UserId(uuid);
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    void testToStringReturnsUUID() {
        UserId id = UserId.generate();
        String str = id.toString();
        // Should be parseable back
        UserId id2 = UserId.of(str);
        assertEquals(id, id2);
    }

    @Test
    void testNullFromUUIDThrows() {
        assertThrows(NullPointerException.class, () -> new UserId(null));
    }

    @Test
    void testNullFromStringThrows() {
        assertThrows(NullPointerException.class, () -> UserId.of(null));
    }
}

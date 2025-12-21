package com.oodesigns.cas.domain.value;

import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserId value object.
 * Validates: generation, parsing, immutability, equality, hashCode.
 */
public class UserIdTest {

    @Test
    public void testGenerateCreatesValidUUID() {
        UserId id = UserId.generate();
        assertNotNull(id);
        assertNotNull(id.asUUID());
        assertNotNull(id.asString());
    }

    @Test
    public void testFromUUID() {
        UUID uuid = UUID.randomUUID();
        UserId id = new UserId(uuid);
        assertEquals(uuid, id.asUUID());
    }

    @Test
    public void testFromString() {
        String uuidStr = UUID.randomUUID().toString();
        UserId id = UserId.of(uuidStr);
        assertEquals(uuidStr, id.asString());
    }

    @Test
    public void testFromInvalidStringThrows() {
        assertThrows(IllegalArgumentException.class, () -> UserId.of("not-a-uuid"));
    }

    @Test
    public void testEqualityBasedOnUUID() {
        UUID uuid = UUID.randomUUID();
        UserId id1 = new UserId(uuid);
        UserId id2 = new UserId(uuid);
        assertEquals(id1, id2);
    }

    @Test
    public void testInequalityDifferentUUIDs() {
        UserId id1 = UserId.generate();
        UserId id2 = UserId.generate();
        assertNotEquals(id1, id2);
    }

    @Test
    public void testHashCodeConsistency() {
        UUID uuid = UUID.randomUUID();
        UserId id1 = new UserId(uuid);
        UserId id2 = new UserId(uuid);
        assertEquals(id1.hashCode(), id2.hashCode());
    }

    @Test
    public void testToStringReturnsUUID() {
        UserId id = UserId.generate();
        String str = id.asString();
        // Should be parseable back
        UserId id2 = UserId.of(str);
        assertEquals(id, id2);
    }

    @Test
    public void testNullFromUUIDThrows() {
        assertThrows(NullPointerException.class, () -> new UserId(null));
    }

    @Test
    public void testNullFromStringThrows() {
        assertThrows(NullPointerException.class, () -> UserId.of(null));
    }
}

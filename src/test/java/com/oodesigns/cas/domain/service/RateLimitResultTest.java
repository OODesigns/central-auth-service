package com.oodesigns.cas.domain.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimitResult sealed interface.
 * Validates: fluent mapTo/orElse pattern, state handling, factory methods.
 */
class RateLimitResultTest {

    @Test
    void testAllowedState() {
        Ports.RateLimitResult result = Ports.RateLimitResult.allowed();

        String mapped = result
            .mapTo(allowed -> "success")
            .orElse(blocked -> "blocked: " + blocked.message());

        assertEquals("success", mapped);
    }

    @Test
    void testBlockedState() {
        Ports.RateLimitResult result = Ports.RateLimitResult.blocked("Rate limit exceeded");

        String mapped = result
            .mapTo(allowed -> "success")
            .orElse(blocked -> "blocked: " + blocked.message());

        assertEquals("blocked: Rate limit exceeded", mapped);
    }

    @Test
    void testAllowedMapToAppliesFunction() {
        Ports.RateLimitResult result = Ports.RateLimitResult.allowed();

        Integer value = result
            .mapTo(allowed -> 42)
            .orElse(blocked -> -1);

        assertEquals(42, value);
    }

    @Test
    void testBlockedMapToIgnoresSuccessFunction() {
        Ports.RateLimitResult result = Ports.RateLimitResult.blocked("Too many requests");

        Integer value = result
            .mapTo(allowed -> {
                fail("Success function should not be called for blocked state");
                return 42;
            })
            .orElse(blocked -> -1);

        assertEquals(-1, value);
    }

    @Test
    void testBlockedOrElseAppliesFunction() {
        Ports.RateLimitResult result = Ports.RateLimitResult.blocked("Limit reached");

        String message = result
            .mapTo(allowed -> "allowed")
            .orElse(blocked -> blocked.message().toUpperCase());

        assertEquals("LIMIT REACHED", message);
    }

    @Test
    void testAllowedOrElseIgnoresFailureFunction() {
        Ports.RateLimitResult result = Ports.RateLimitResult.allowed();

        String value = result
            .mapTo(allowed -> "success")
            .orElse(blocked -> {
                fail("Failure function should not be called for allowed state");
                return "failed";
            });

        assertEquals("success", value);
    }

    @Test
    void testBlockedWithNullMessageThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> Ports.RateLimitResult.blocked(null));
    }

    @Test
    void testBlockedWithEmptyMessageThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> Ports.RateLimitResult.blocked(""));
    }

    @Test
    void testBlockedWithBlankMessageThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> Ports.RateLimitResult.blocked("   "));
    }

    @Test
    void testBlockedMessagePreserved() {
        String expectedMessage = "Rate limit exceeded for key:login:192.168.1.1";
        Ports.RateLimitResult result = Ports.RateLimitResult.blocked(expectedMessage);

        String actualMessage = result
            .mapTo(allowed -> "")
            .orElse(blocked -> blocked.message());

        assertEquals(expectedMessage, actualMessage);
    }

    @Test
    void testMultipleAllowedResults() {
        Ports.RateLimitResult result1 = Ports.RateLimitResult.allowed();
        Ports.RateLimitResult result2 = Ports.RateLimitResult.allowed();

        String value1 = result1.mapTo(a -> "first").orElse(b -> "blocked");
        String value2 = result2.mapTo(a -> "second").orElse(b -> "blocked");

        assertEquals("first", value1);
        assertEquals("second", value2);
    }

    @Test
    void testMultipleBlockedResults() {
        Ports.RateLimitResult result1 = Ports.RateLimitResult.blocked("Error 1");
        Ports.RateLimitResult result2 = Ports.RateLimitResult.blocked("Error 2");

        String msg1 = result1.mapTo(a -> "ok").orElse(Ports.RateLimitResult.Blocked::message);
        String msg2 = result2.mapTo(a -> "ok").orElse(Ports.RateLimitResult.Blocked::message);

        assertEquals("Error 1", msg1);
        assertEquals("Error 2", msg2);
        assertNotEquals(msg1, msg2);
    }

    @Test
    void testFluentChaining() {
        Ports.RateLimitResult allowed = Ports.RateLimitResult.allowed();
        Ports.RateLimitResult blocked = Ports.RateLimitResult.blocked("Too many attempts");

        boolean allowedResult = allowed
            .mapTo(a -> true)
            .orElse(b -> false);

        boolean blockedResult = blocked
            .mapTo(a -> true)
            .orElse(b -> false);

        assertTrue(allowedResult);
        assertFalse(blockedResult);
    }

    @Test
    void testComplexTypeMapping() {
        record Response(boolean success, String message) {}

        Ports.RateLimitResult allowed = Ports.RateLimitResult.allowed();
        Ports.RateLimitResult blocked = Ports.RateLimitResult.blocked("Rate exceeded");

        Response allowedResponse = allowed
            .mapTo(a -> new Response(true, "Allowed"))
            .orElse(b -> new Response(false, b.message()));

        Response blockedResponse = blocked
            .mapTo(a -> new Response(true, "Allowed"))
            .orElse(b -> new Response(false, b.message()));

        assertTrue(allowedResponse.success());
        assertEquals("Allowed", allowedResponse.message());

        assertFalse(blockedResponse.success());
        assertEquals("Rate exceeded", blockedResponse.message());
    }

    @Test
    void testNestedResults() {
        Ports.RateLimitResult outerAllowed = Ports.RateLimitResult.allowed();
        Ports.RateLimitResult innerAllowed = Ports.RateLimitResult.allowed();

        String result = outerAllowed
            .mapTo(outer -> innerAllowed
                .mapTo(inner -> "both allowed")
                .orElse(innerBlocked -> "inner blocked"))
            .orElse(outerBlocked -> "outer blocked");

        assertEquals("both allowed", result);
    }

    @Test
    void testAllowedIsSealed() {
        Ports.RateLimitResult result = Ports.RateLimitResult.allowed();
        assertInstanceOf(Ports.RateLimitResult.Allowed.class, result);
    }

    @Test
    void testBlockedIsSealed() {
        Ports.RateLimitResult result = Ports.RateLimitResult.blocked("error");
        assertInstanceOf(Ports.RateLimitResult.Blocked.class, result);
    }
}

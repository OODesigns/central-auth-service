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
            .mapTo(ignored -> "success")
            .orElse(blocked -> "blocked: %s".formatted(blocked.message()));

        assertEquals("success", mapped);
    }

    @Test
    void testBlockedState() {
        Ports.RateLimitResult result = Ports.RateLimitResult.blocked("Rate limit exceeded");

        String mapped = result
            .mapTo(ignored -> "success")
            .orElse(blocked -> "blocked: %s".formatted(blocked.message()));

        assertEquals("blocked: Rate limit exceeded", mapped);
    }

    @Test
    void testAllowedMapToAppliesFunction() {
        Ports.RateLimitResult result = Ports.RateLimitResult.allowed();

        Integer value = result
            .mapTo(ignored -> 42)
            .orElse(ignored -> -1);

        assertEquals(42, value);
    }

    @Test
    void testBlockedMapToIgnoresSuccessFunction() {
        Ports.RateLimitResult result = Ports.RateLimitResult.blocked("Too many requests");

        Integer value = result
            .mapTo(ignored -> {
                fail("Success function should not be called for blocked state");
                return 42;
            })
            .orElse(ignored -> -1);

        assertEquals(-1, value);
    }

    @Test
    void testBlockedOrElseAppliesFunction() {
        Ports.RateLimitResult result = Ports.RateLimitResult.blocked("Limit reached");

        String message = result
            .mapTo(ignored -> "allowed")
            .orElse(blocked -> blocked.message().toUpperCase());

        assertEquals("LIMIT REACHED", message);
    }

    @Test
    void testAllowedOrElseIgnoresFailureFunction() {
        Ports.RateLimitResult result = Ports.RateLimitResult.allowed();

        String value = result
            .mapTo(ignored -> "success")
            .orElse(ignored -> {
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
            .mapTo(ignored -> "")
            .orElse(Ports.RateLimitResult.Blocked::message);

        assertEquals(expectedMessage, actualMessage);
    }

    @Test
    void testMultipleAllowedResults() {
        Ports.RateLimitResult result1 = Ports.RateLimitResult.allowed();
        Ports.RateLimitResult result2 = Ports.RateLimitResult.allowed();

        String value1 = result1.mapTo(ignored -> "first").orElse(ignored -> "blocked");
        String value2 = result2.mapTo(ignored -> "second").orElse(ignored -> "blocked");

        assertEquals("first", value1);
        assertEquals("second", value2);
    }

    @Test
    void testMultipleBlockedResults() {
        Ports.RateLimitResult result1 = Ports.RateLimitResult.blocked("Error 1");
        Ports.RateLimitResult result2 = Ports.RateLimitResult.blocked("Error 2");

        String msg1 = result1.mapTo(ignored -> "ok").orElse(Ports.RateLimitResult.Blocked::message);
        String msg2 = result2.mapTo(ignored -> "ok").orElse(Ports.RateLimitResult.Blocked::message);

        assertEquals("Error 1", msg1);
        assertEquals("Error 2", msg2);
        assertNotEquals(msg1, msg2);
    }

    @Test
    void testFluentChaining() {
        Ports.RateLimitResult allowed = Ports.RateLimitResult.allowed();
        Ports.RateLimitResult blocked = Ports.RateLimitResult.blocked("Too many attempts");

        boolean allowedResult = allowed
            .mapTo(ignored -> true)
            .orElse(ignored -> false);

        boolean blockedResult = blocked
            .mapTo(ignored -> true)
            .orElse(ignored -> false);

        assertTrue(allowedResult);
        assertFalse(blockedResult);
    }

    @Test
    void testComplexTypeMapping() {
        record Response(boolean success, String message) {}

        Ports.RateLimitResult allowed = Ports.RateLimitResult.allowed();
        Ports.RateLimitResult blocked = Ports.RateLimitResult.blocked("Rate exceeded");

        Response allowedResponse = allowed
            .mapTo(ignored -> new Response(true, "Allowed"))
            .orElse(b -> new Response(false, b.message()));

        Response blockedResponse = blocked
            .mapTo(ignored -> new Response(true, "Allowed"))
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
            .mapTo(ignoredOuter -> innerAllowed
                .mapTo(ignoredInner -> "both allowed")
                .orElse(ignoredInnerBlocked -> "inner blocked"))
            .orElse(ignoredOuterBlocked -> "outer blocked");

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

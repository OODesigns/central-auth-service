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
        final Ports.RateLimitResult result = Ports.RateLimitResult.allowed();

        final String mapped = result
            .mapTo(ignored -> "success")
            .orElse(blocked -> "blocked: %s".formatted(blocked.message()));

        assertEquals("success", mapped);
    }

    @Test
    void testBlockedState() {
        final Ports.RateLimitResult result = Ports.RateLimitResult.blocked("Rate limit exceeded");

        final String mapped = result
            .mapTo(ignored -> "success")
            .orElse(blocked -> "blocked: %s".formatted(blocked.message()));

        assertEquals("blocked: Rate limit exceeded", mapped);
    }

    @Test
    void testAllowedMapToAppliesFunction() {
        final Ports.RateLimitResult result = Ports.RateLimitResult.allowed();

        final Integer value = result
            .mapTo(ignored -> 42)
            .orElse(ignored -> -1);

        assertEquals(42, value);
    }

    @Test
    void testBlockedMapToIgnoresSuccessFunction() {
        final Ports.RateLimitResult result = Ports.RateLimitResult.blocked("Too many requests");

        final Integer value = result
            .mapTo(ignored -> {
                fail("Success function should not be called for blocked state");
                return 42;
            })
            .orElse(ignored -> -1);

        assertEquals(-1, value);
    }

    @Test
    void testBlockedOrElseAppliesFunction() {
        final Ports.RateLimitResult result = Ports.RateLimitResult.blocked("Limit reached");

        final String message = result
            .mapTo(ignored -> "allowed")
            .orElse(blocked -> blocked.message().toUpperCase());

        assertEquals("LIMIT REACHED", message);
    }

    @Test
    void testAllowedOrElseIgnoresFailureFunction() {
        final Ports.RateLimitResult result = Ports.RateLimitResult.allowed();

        final String value = result
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
        final String expectedMessage = "Rate limit exceeded for key:login:192.168.1.1";
        final Ports.RateLimitResult result = Ports.RateLimitResult.blocked(expectedMessage);

        final String actualMessage = result
            .mapTo(ignored -> "")
            .orElse(Ports.RateLimitResult.Blocked::message);

        assertEquals(expectedMessage, actualMessage);
    }

    @Test
    void testMultipleAllowedResults() {
        final Ports.RateLimitResult result1 = Ports.RateLimitResult.allowed();
        final Ports.RateLimitResult result2 = Ports.RateLimitResult.allowed();

        final String value1 = result1.mapTo(ignored -> "first").orElse(ignored -> "blocked");
        final String value2 = result2.mapTo(ignored -> "second").orElse(ignored -> "blocked");

        assertEquals("first", value1);
        assertEquals("second", value2);
    }

    @Test
    void testMultipleBlockedResults() {
        final Ports.RateLimitResult result1 = Ports.RateLimitResult.blocked("Error 1");
        final Ports.RateLimitResult result2 = Ports.RateLimitResult.blocked("Error 2");

        final String msg1 = result1.mapTo(ignored -> "ok").orElse(Ports.RateLimitResult.Blocked::message);
        final String msg2 = result2.mapTo(ignored -> "ok").orElse(Ports.RateLimitResult.Blocked::message);

        assertEquals("Error 1", msg1);
        assertEquals("Error 2", msg2);
        assertNotEquals(msg1, msg2);
    }

    @Test
    void testFluentChaining() {
        final Ports.RateLimitResult allowed = Ports.RateLimitResult.allowed();
        final Ports.RateLimitResult blocked = Ports.RateLimitResult.blocked("Too many attempts");

        final boolean allowedResult = allowed
            .mapTo(ignored -> true)
            .orElse(ignored -> false);

        final boolean blockedResult = blocked
            .mapTo(ignored -> true)
            .orElse(ignored -> false);

        assertTrue(allowedResult);
        assertFalse(blockedResult);
    }

    @Test
    void testComplexTypeMapping() {
        record Response(boolean success, String message) {}

        final Ports.RateLimitResult allowed = Ports.RateLimitResult.allowed();
        final Ports.RateLimitResult blocked = Ports.RateLimitResult.blocked("Rate exceeded");

        final Response allowedResponse = allowed
            .mapTo(ignored -> new Response(true, "Allowed"))
            .orElse(b -> new Response(false, b.message()));

        final Response blockedResponse = blocked
            .mapTo(ignored -> new Response(true, "Allowed"))
            .orElse(b -> new Response(false, b.message()));

        assertTrue(allowedResponse.success());
        assertEquals("Allowed", allowedResponse.message());

        assertFalse(blockedResponse.success());
        assertEquals("Rate exceeded", blockedResponse.message());
    }

    @Test
    void testNestedResults() {
        final Ports.RateLimitResult outerAllowed = Ports.RateLimitResult.allowed();
        final Ports.RateLimitResult innerAllowed = Ports.RateLimitResult.allowed();

        final String result = outerAllowed
            .mapTo(ignoredOuter -> innerAllowed
                .mapTo(ignoredInner -> "both allowed")
                .orElse(ignoredInnerBlocked -> "inner blocked"))
            .orElse(ignoredOuterBlocked -> "outer blocked");

        assertEquals("both allowed", result);
    }

    @Test
    void testAllowedIsSealed() {
        final Ports.RateLimitResult result = Ports.RateLimitResult.allowed();
        assertInstanceOf(Ports.RateLimitResult.Allowed.class, result);
    }

    @Test
    void testBlockedIsSealed() {
        final Ports.RateLimitResult result = Ports.RateLimitResult.blocked("error");
        assertInstanceOf(Ports.RateLimitResult.Blocked.class, result);
    }
}

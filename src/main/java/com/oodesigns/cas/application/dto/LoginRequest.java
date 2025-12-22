package com.oodesigns.cas.application.dto;

/**
 * Transport DTO for login request.
 * Shaped for REST API; maps to LoginCommand.
 */
public record LoginRequest(String username, String password) {
}

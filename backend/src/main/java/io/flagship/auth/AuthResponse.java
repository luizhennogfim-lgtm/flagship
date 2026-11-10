package io.flagship.auth;

import java.util.UUID;

import io.flagship.organization.Role;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UUID organizationId,
        String organizationSlug,
        Role role) {
}

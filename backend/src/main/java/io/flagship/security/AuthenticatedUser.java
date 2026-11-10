package io.flagship.security;

import java.util.UUID;

import io.flagship.organization.Role;

/** The authenticated caller, rebuilt from the JWT on every request. */
public record AuthenticatedUser(UUID userId, UUID organizationId, Role role) {
}

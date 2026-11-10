package io.flagship.security;

import java.util.UUID;

/** The caller when an application authenticates with an API key (instead of a person with a JWT). */
public record ApiKeyPrincipal(UUID organizationId, UUID apiKeyId) {
}

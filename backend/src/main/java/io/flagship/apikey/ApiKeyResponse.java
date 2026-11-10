package io.flagship.apikey;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyResponse(UUID id, String name, String prefix, Instant createdAt, Instant revokedAt) {

    static ApiKeyResponse from(ApiKey key) {
        return new ApiKeyResponse(key.getId(), key.getName(), key.getKeyPrefix(), key.getCreatedAt(), key.getRevokedAt());
    }
}

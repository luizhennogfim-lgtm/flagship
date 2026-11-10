package io.flagship.apikey;

import java.time.Instant;
import java.util.UUID;

/** "key" is the only time the full key is ever returned. Save it now. */
public record CreatedApiKeyResponse(UUID id, String name, String prefix, String key, Instant createdAt) {
}

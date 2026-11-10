package io.flagship.environment;

import java.time.Instant;
import java.util.UUID;

public record EnvironmentResponse(UUID id, String key, String name, Instant createdAt) {

    static EnvironmentResponse from(Environment environment) {
        return new EnvironmentResponse(environment.getId(), environment.getEnvKey(),
                environment.getName(), environment.getCreatedAt());
    }
}

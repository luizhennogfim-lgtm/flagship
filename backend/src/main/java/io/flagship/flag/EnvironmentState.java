package io.flagship.flag;

import java.time.Instant;
import java.util.UUID;

import io.flagship.environment.Environment;

public record EnvironmentState(
        String environmentKey,
        String environmentName,
        boolean enabled,
        int rolloutPercentage,
        long version,
        UUID updatedBy,
        Instant updatedAt) {

    static EnvironmentState of(Environment environment, FlagEnvironmentConfig config) {
        return new EnvironmentState(environment.getEnvKey(), environment.getName(), config.isEnabled(),
                config.getRolloutPercentage(), config.getVersion(), config.getUpdatedBy(), config.getUpdatedAt());
    }
}

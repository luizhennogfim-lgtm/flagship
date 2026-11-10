package io.flagship.flag;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FlagResponse(
        UUID id,
        String key,
        String name,
        String description,
        boolean archived,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        List<EnvironmentState> environments) {

    static FlagResponse of(FeatureFlag flag, List<EnvironmentState> environments) {
        return new FlagResponse(flag.getId(), flag.getFlagKey(), flag.getName(), flag.getDescription(),
                flag.isArchived(), flag.getCreatedBy(), flag.getCreatedAt(), flag.getUpdatedAt(), environments);
    }
}

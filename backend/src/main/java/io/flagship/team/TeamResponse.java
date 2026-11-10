package io.flagship.team;

import java.time.Instant;
import java.util.UUID;

public record TeamResponse(UUID id, String name, Instant createdAt) {

    static TeamResponse from(Team team) {
        return new TeamResponse(team.getId(), team.getName(), team.getCreatedAt());
    }
}

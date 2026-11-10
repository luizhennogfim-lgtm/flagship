package io.flagship.flag;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FlagEnvironmentConfigRepository extends JpaRepository<FlagEnvironmentConfig, UUID> {

    List<FlagEnvironmentConfig> findByFlagIdIn(Collection<UUID> flagIds);

    Optional<FlagEnvironmentConfig> findByFlagIdAndEnvironmentId(UUID flagId, UUID environmentId);
}

package io.flagship.environment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {

    List<Environment> findByOrganizationIdOrderByCreatedAtAscEnvKeyAsc(UUID organizationId);

    Optional<Environment> findByOrganizationIdAndEnvKey(UUID organizationId, String envKey);

    boolean existsByOrganizationIdAndEnvKey(UUID organizationId, String envKey);
}

package io.flagship.apikey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHashAndRevokedAtIsNull(String keyHash);

    List<ApiKey> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    Optional<ApiKey> findByIdAndOrganizationId(UUID id, UUID organizationId);
}

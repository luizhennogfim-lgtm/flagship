package io.flagship.flag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

    Optional<FeatureFlag> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndFlagKey(UUID organizationId, String flagKey);

    Optional<FeatureFlag> findByOrganizationIdAndFlagKey(UUID organizationId, String flagKey);

    List<FeatureFlag> findByOrganizationId(UUID organizationId);

    /** pattern is already lower-case and wrapped in %...% */
    @Query(value = """
            select f from FeatureFlag f
            where f.organizationId = :organizationId and f.archived = :archived
              and (lower(f.flagKey) like :pattern or lower(f.name) like :pattern)
            order by f.createdAt desc
            """,
            countQuery = """
            select count(f) from FeatureFlag f
            where f.organizationId = :organizationId and f.archived = :archived
              and (lower(f.flagKey) like :pattern or lower(f.name) like :pattern)
            """)
    Page<FeatureFlag> search(@Param("organizationId") UUID organizationId,
                             @Param("archived") boolean archived,
                             @Param("pattern") String pattern,
                             Pageable pageable);

    /** Same as search, but only flags whose state in the given environment equals "enabled". */
    @Query(value = """
            select f from FeatureFlag f
            where f.organizationId = :organizationId and f.archived = :archived
              and (lower(f.flagKey) like :pattern or lower(f.name) like :pattern)
              and exists (select c.id from FlagEnvironmentConfig c, Environment e
                          where c.flagId = f.id and e.id = c.environmentId
                            and e.organizationId = :organizationId
                            and e.envKey = :environmentKey and c.enabled = :enabled)
            order by f.createdAt desc
            """,
            countQuery = """
            select count(f) from FeatureFlag f
            where f.organizationId = :organizationId and f.archived = :archived
              and (lower(f.flagKey) like :pattern or lower(f.name) like :pattern)
              and exists (select c.id from FlagEnvironmentConfig c, Environment e
                          where c.flagId = f.id and e.id = c.environmentId
                            and e.organizationId = :organizationId
                            and e.envKey = :environmentKey and c.enabled = :enabled)
            """)
    Page<FeatureFlag> searchByEnvironmentState(@Param("organizationId") UUID organizationId,
                                               @Param("archived") boolean archived,
                                               @Param("pattern") String pattern,
                                               @Param("environmentKey") String environmentKey,
                                               @Param("enabled") boolean enabled,
                                               Pageable pageable);
}

package io.flagship.environment;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.flagship.audit.AuditAction;
import io.flagship.audit.AuditService;
import io.flagship.cache.CacheInvalidator;
import io.flagship.common.exception.ConflictException;
import io.flagship.common.tenant.TenantContext;
import io.flagship.flag.FeatureFlag;
import io.flagship.flag.FeatureFlagRepository;
import io.flagship.flag.FlagEnvironmentConfig;
import io.flagship.flag.FlagEnvironmentConfigRepository;

@Service
public class EnvironmentService {

    private final EnvironmentRepository environments;
    private final FeatureFlagRepository flags;
    private final FlagEnvironmentConfigRepository configs;
    private final AuditService audit;
    private final CacheInvalidator cache;

    public EnvironmentService(EnvironmentRepository environments, FeatureFlagRepository flags,
                              FlagEnvironmentConfigRepository configs, AuditService audit, CacheInvalidator cache) {
        this.environments = environments;
        this.flags = flags;
        this.configs = configs;
        this.audit = audit;
        this.cache = cache;
    }

    /** Called when an organization is created: Development, Staging and Production, in that order. */
    @Transactional
    public void seedDefaults(UUID organizationId) {
        Instant base = Instant.now();
        environments.save(new Environment(organizationId, "development", "Development", base));
        environments.save(new Environment(organizationId, "staging", "Staging", base.plusMillis(1)));
        environments.save(new Environment(organizationId, "production", "Production", base.plusMillis(2)));
    }

    @Transactional(readOnly = true)
    public List<EnvironmentResponse> list() {
        return environments.findByOrganizationIdOrderByCreatedAtAscEnvKeyAsc(TenantContext.require()).stream()
                .map(EnvironmentResponse::from)
                .toList();
    }

    /** Creates the environment and gives every existing flag an (OFF, 0%) state in it. */
    @Transactional
    public EnvironmentResponse create(CreateEnvironmentRequest request) {
        UUID organizationId = TenantContext.require();
        String key = request.key().trim();
        if (environments.existsByOrganizationIdAndEnvKey(organizationId, key)) {
            throw new ConflictException("An environment with key '" + key + "' already exists");
        }
        Environment environment = environments.save(new Environment(organizationId, key, request.name().trim()));
        for (FeatureFlag flag : flags.findByOrganizationId(organizationId)) {
            configs.save(new FlagEnvironmentConfig(flag.getId(), environment.getId()));
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("key", environment.getEnvKey());
        snapshot.put("name", environment.getName());
        audit.record("ENVIRONMENT", environment.getId(), AuditAction.ENVIRONMENT_CREATED, null, snapshot);
        cache.afterCommitInvalidate(organizationId);
        return EnvironmentResponse.from(environment);
    }
}

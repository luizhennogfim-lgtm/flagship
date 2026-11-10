package io.flagship.flag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.flagship.audit.AuditAction;
import io.flagship.audit.AuditService;
import io.flagship.cache.CacheInvalidator;
import io.flagship.common.api.PageResponse;
import io.flagship.common.api.Paging;
import io.flagship.common.exception.BusinessRuleException;
import io.flagship.common.exception.ConflictException;
import io.flagship.common.exception.ResourceNotFoundException;
import io.flagship.common.tenant.TenantContext;
import io.flagship.environment.Environment;
import io.flagship.environment.EnvironmentRepository;
import io.flagship.security.AuthenticatedUser;
import io.flagship.security.CurrentUser;

/** Every query is scoped to the organization of the current request (tenant isolation). */
@Service
public class FlagService {

    private static final String ENTITY_TYPE = "FEATURE_FLAG";

    private final FeatureFlagRepository flags;
    private final FlagEnvironmentConfigRepository configs;
    private final EnvironmentRepository environments;
    private final AuditService audit;
    private final CacheInvalidator cache;

    public FlagService(FeatureFlagRepository flags, FlagEnvironmentConfigRepository configs,
                       EnvironmentRepository environments, AuditService audit, CacheInvalidator cache) {
        this.flags = flags;
        this.configs = configs;
        this.environments = environments;
        this.audit = audit;
        this.cache = cache;
    }

    @Transactional
    public FlagResponse create(CreateFlagRequest request) {
        AuthenticatedUser actor = CurrentUser.get();
        UUID organizationId = actor.organizationId();
        String key = request.key().trim();
        if (flags.existsByOrganizationIdAndFlagKey(organizationId, key)) {
            throw new ConflictException("A flag with key '" + key + "' already exists");
        }
        FeatureFlag flag = flags.save(new FeatureFlag(organizationId, key, request.name().trim(),
                blankToNull(request.description()), actor.userId()));
        for (Environment environment : environments.findByOrganizationIdOrderByCreatedAtAscEnvKeyAsc(organizationId)) {
            configs.save(new FlagEnvironmentConfig(flag.getId(), environment.getId()));
        }
        audit.record(ENTITY_TYPE, flag.getId(), AuditAction.FLAG_CREATED, null, flagSnapshot(flag));
        cache.afterCommitInvalidate(organizationId);
        return toResponse(flag);
    }

    @Transactional(readOnly = true)
    public PageResponse<FlagResponse> list(String search, String environment, Boolean enabled,
                                           boolean archived, int page, int size) {
        UUID organizationId = TenantContext.require();
        boolean hasEnvironment = environment != null && !environment.isBlank();
        if (hasEnvironment != (enabled != null)) {
            throw new BusinessRuleException("To filter by state, send both 'environment' and 'enabled'");
        }
        String pattern = "%" + (search == null ? "" : search.trim().toLowerCase(Locale.ROOT)) + "%";
        Page<FeatureFlag> result = hasEnvironment
                ? flags.searchByEnvironmentState(organizationId, archived, pattern, environment.trim(), enabled, Paging.of(page, size))
                : flags.search(organizationId, archived, pattern, Paging.of(page, size));
        return new PageResponse<>(toResponses(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public FlagResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional
    public FlagResponse update(UUID id, UpdateFlagRequest request) {
        FeatureFlag flag = find(id);
        requireActive(flag);
        Map<String, Object> before = flagSnapshot(flag);
        flag.rename(request.name().trim(), blankToNull(request.description()));
        audit.record(ENTITY_TYPE, flag.getId(), AuditAction.FLAG_UPDATED, before, flagSnapshot(flag));
        return toResponse(flag);
    }

    /** Archiving is a soft delete: the flag and its history stay, but it can no longer be changed. */
    @Transactional
    public void archive(UUID id) {
        FeatureFlag flag = find(id);
        if (flag.isArchived()) {
            return;
        }
        Map<String, Object> before = flagSnapshot(flag);
        flag.archive();
        audit.record(ENTITY_TYPE, flag.getId(), AuditAction.FLAG_ARCHIVED, before, flagSnapshot(flag));
        cache.afterCommitInvalidate(flag.getOrganizationId());
    }

    @Transactional
    public EnvironmentState updateEnvironment(UUID flagId, String environmentKey, UpdateEnvironmentStateRequest request) {
        UUID organizationId = TenantContext.require();
        FeatureFlag flag = find(flagId);
        requireActive(flag);
        Environment environment = environments.findByOrganizationIdAndEnvKey(organizationId, environmentKey)
                .orElseThrow(() -> new ResourceNotFoundException("Environment", environmentKey));
        FlagEnvironmentConfig config = configs.findByFlagIdAndEnvironmentId(flag.getId(), environment.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Flag state in environment", environmentKey));

        if (config.getVersion() != request.version()) {
            throw new ConflictException("This flag was changed by someone else; reload it and try again");
        }
        Map<String, Object> before = stateSnapshot(flag, environment, config);
        config.update(request.enabled(), request.rolloutPercentage(), CurrentUser.get().userId());
        // flush now so a concurrent writer is detected here, inside the request, as a 409
        config = configs.saveAndFlush(config);
        audit.record(ENTITY_TYPE, flag.getId(), AuditAction.FLAG_ENVIRONMENT_UPDATED, before,
                stateSnapshot(flag, environment, config));
        cache.afterCommitInvalidate(organizationId);
        return EnvironmentState.of(environment, config);
    }

    // ---------- helpers ----------

    private FeatureFlag find(UUID id) {
        return flags.findByIdAndOrganizationId(id, TenantContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("Flag", id));
    }

    private static void requireActive(FeatureFlag flag) {
        if (flag.isArchived()) {
            throw new BusinessRuleException("Archived flags cannot be changed");
        }
    }

    private FlagResponse toResponse(FeatureFlag flag) {
        return toResponses(List.of(flag)).get(0);
    }

    private List<FlagResponse> toResponses(List<FeatureFlag> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Environment> orgEnvironments =
                environments.findByOrganizationIdOrderByCreatedAtAscEnvKeyAsc(TenantContext.require());
        Map<UUID, List<FlagEnvironmentConfig>> configsByFlag =
                configs.findByFlagIdIn(list.stream().map(FeatureFlag::getId).toList()).stream()
                        .collect(Collectors.groupingBy(FlagEnvironmentConfig::getFlagId));
        return list.stream()
                .map(flag -> FlagResponse.of(flag, states(orgEnvironments, configsByFlag.getOrDefault(flag.getId(), List.of()))))
                .toList();
    }

    private static List<EnvironmentState> states(List<Environment> orgEnvironments, List<FlagEnvironmentConfig> flagConfigs) {
        Map<UUID, FlagEnvironmentConfig> byEnvironment = flagConfigs.stream()
                .collect(Collectors.toMap(FlagEnvironmentConfig::getEnvironmentId, Function.identity()));
        List<EnvironmentState> result = new ArrayList<>();
        for (Environment environment : orgEnvironments) {
            FlagEnvironmentConfig config = byEnvironment.get(environment.getId());
            if (config != null) {
                result.add(EnvironmentState.of(environment, config));
            }
        }
        return result;
    }

    private static Map<String, Object> flagSnapshot(FeatureFlag flag) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("key", flag.getFlagKey());
        snapshot.put("name", flag.getName());
        snapshot.put("description", flag.getDescription());
        snapshot.put("archived", flag.isArchived());
        return snapshot;
    }

    private static Map<String, Object> stateSnapshot(FeatureFlag flag, Environment environment, FlagEnvironmentConfig config) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("flagKey", flag.getFlagKey());
        snapshot.put("environment", environment.getEnvKey());
        snapshot.put("enabled", config.isEnabled());
        snapshot.put("rolloutPercentage", config.getRolloutPercentage());
        return snapshot;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

package io.flagship.flag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.flagship.audit.AuditAction;
import io.flagship.audit.AuditService;
import io.flagship.cache.CacheInvalidator;
import io.flagship.common.exception.BusinessRuleException;
import io.flagship.common.exception.ConflictException;
import io.flagship.common.exception.ResourceNotFoundException;
import io.flagship.common.tenant.TenantContext;
import io.flagship.environment.Environment;
import io.flagship.environment.EnvironmentRepository;
import io.flagship.security.CurrentUser;

@Service
public class RuleService {

    private final FeatureFlagRepository flags;
    private final EnvironmentRepository environments;
    private final FlagEnvironmentConfigRepository configs;
    private final FlagRuleRepository rules;
    private final AuditService audit;
    private final CacheInvalidator cache;

    public RuleService(FeatureFlagRepository flags, EnvironmentRepository environments,
                       FlagEnvironmentConfigRepository configs, FlagRuleRepository rules, AuditService audit,
                       CacheInvalidator cache) {
        this.flags = flags;
        this.environments = environments;
        this.configs = configs;
        this.rules = rules;
        this.audit = audit;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public RulesResponse get(UUID flagId, String environmentKey) {
        Target target = resolve(flagId, environmentKey);
        return toResponse(target.config().getVersion(), rules.findByFlagConfigIdOrderByPriorityAsc(target.config().getId()));
    }

    @Transactional
    public RulesResponse replace(UUID flagId, String environmentKey, ReplaceRulesRequest request) {
        Target target = resolve(flagId, environmentKey);
        if (target.flag().isArchived()) {
            throw new BusinessRuleException("Archived flags cannot be changed");
        }
        FlagEnvironmentConfig config = target.config();
        if (config.getVersion() != request.version()) {
            throw new ConflictException("This flag was changed by someone else; reload it and try again");
        }
        request.rules().forEach(RuleService::validate);

        List<FlagRule> old = rules.findByFlagConfigIdOrderByPriorityAsc(config.getId());
        Map<String, Object> before = snapshot(target, old);

        rules.deleteAll(old);
        List<FlagRule> created = new ArrayList<>();
        for (int i = 0; i < request.rules().size(); i++) {
            RuleRequest r = request.rules().get(i);
            boolean usesAttribute = r.type() == RuleType.ATTRIBUTE;
            created.add(new FlagRule(config.getId(), i, r.type(),
                    usesAttribute ? r.attribute().trim() : null,
                    usesAttribute ? r.operator() : null,
                    List.copyOf(r.values()), r.serve(), blankToNull(r.description())));
        }
        rules.saveAll(created);

        config.markUpdated(CurrentUser.get().userId());
        FlagEnvironmentConfig saved = configs.saveAndFlush(config);
        audit.record("FEATURE_FLAG", target.flag().getId(), AuditAction.FLAG_RULES_UPDATED, before,
                snapshot(target, created));
        cache.afterCommitInvalidate(target.flag().getOrganizationId());
        return toResponse(saved.getVersion(), created);
    }

    // ---------- validation ----------

    static void validate(RuleRequest rule) {
        switch (rule.type()) {
            case USER_LIST, GROUP -> {
                boolean hasAttribute = rule.attribute() != null && !rule.attribute().isBlank();
                if (hasAttribute || rule.operator() != null) {
                    throw new BusinessRuleException(rule.type() + " rules do not use attribute or operator");
                }
            }
            case ATTRIBUTE -> validateAttributeRule(rule);
        }
    }

    private static void validateAttributeRule(RuleRequest rule) {
        if (rule.attribute() == null || rule.attribute().isBlank()) {
            throw new BusinessRuleException("ATTRIBUTE rules need an attribute");
        }
        if (!rule.attribute().trim().matches("[A-Za-z0-9_.-]+")) {
            throw new BusinessRuleException("Attribute names may only contain letters, digits and _ . -");
        }
        RuleOperator operator = rule.operator();
        if (operator == null) {
            throw new BusinessRuleException("ATTRIBUTE rules need an operator");
        }
        if (!operator.acceptsManyValues() && rule.values().size() != 1) {
            throw new BusinessRuleException("Operator " + operator + " needs exactly one value");
        }
        if (operator.isNumeric()) {
            try {
                Double.parseDouble(rule.values().get(0));
            } catch (NumberFormatException ex) {
                throw new BusinessRuleException("Operator " + operator + " needs a numeric value");
            }
        }
    }

    // ---------- helpers ----------

    private Target resolve(UUID flagId, String environmentKey) {
        UUID organizationId = TenantContext.require();
        FeatureFlag flag = flags.findByIdAndOrganizationId(flagId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Flag", flagId));
        Environment environment = environments.findByOrganizationIdAndEnvKey(organizationId, environmentKey)
                .orElseThrow(() -> new ResourceNotFoundException("Environment", environmentKey));
        FlagEnvironmentConfig config = configs.findByFlagIdAndEnvironmentId(flag.getId(), environment.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Flag state in environment", environmentKey));
        return new Target(flag, environment, config);
    }

    private static RulesResponse toResponse(long version, List<FlagRule> list) {
        return new RulesResponse(version, list.stream().map(RuleResponse::from).toList());
    }

    private static Map<String, Object> snapshot(Target target, List<FlagRule> list) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (FlagRule rule : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", rule.getType().name());
            item.put("attribute", rule.getAttribute());
            item.put("operator", rule.getOperator() == null ? null : rule.getOperator().name());
            item.put("values", rule.getRuleValues());
            item.put("serve", rule.isServeEnabled());
            items.add(item);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("flagKey", target.flag().getFlagKey());
        snapshot.put("environment", target.environment().getEnvKey());
        snapshot.put("rules", items);
        return snapshot;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Target(FeatureFlag flag, Environment environment, FlagEnvironmentConfig config) {
    }
}

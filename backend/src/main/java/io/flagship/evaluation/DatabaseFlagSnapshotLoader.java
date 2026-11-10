package io.flagship.evaluation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.flagship.environment.Environment;
import io.flagship.environment.EnvironmentRepository;
import io.flagship.flag.FeatureFlag;
import io.flagship.flag.FeatureFlagRepository;
import io.flagship.flag.FlagEnvironmentConfig;
import io.flagship.flag.FlagEnvironmentConfigRepository;
import io.flagship.flag.FlagRuleRepository;

@Component
public class DatabaseFlagSnapshotLoader implements FlagSnapshotLoader {

    private final FeatureFlagRepository flags;
    private final EnvironmentRepository environments;
    private final FlagEnvironmentConfigRepository configs;
    private final FlagRuleRepository rules;

    public DatabaseFlagSnapshotLoader(FeatureFlagRepository flags, EnvironmentRepository environments,
                                      FlagEnvironmentConfigRepository configs, FlagRuleRepository rules) {
        this.flags = flags;
        this.environments = environments;
        this.configs = configs;
        this.rules = rules;
    }

    @Override
    @Transactional(readOnly = true)
    public SnapshotLookup load(UUID organizationId, String flagKey, String environmentKey) {
        Optional<FeatureFlag> flag = flags.findByOrganizationIdAndFlagKey(organizationId, flagKey);
        if (flag.isEmpty()) {
            return SnapshotLookup.flagNotFound();
        }
        Optional<Environment> environment = environments.findByOrganizationIdAndEnvKey(organizationId, environmentKey);
        if (environment.isEmpty()) {
            return SnapshotLookup.environmentNotFound();
        }
        FeatureFlag f = flag.get();
        Optional<FlagEnvironmentConfig> config = configs.findByFlagIdAndEnvironmentId(f.getId(), environment.get().getId());
        if (config.isEmpty()) {
            return SnapshotLookup.found(new FlagSnapshot(f.getId(), f.getFlagKey(), f.isArchived(), false, 0, List.of()));
        }
        FlagEnvironmentConfig c = config.get();
        List<RuleSnapshot> ruleSnapshots = rules.findByFlagConfigIdOrderByPriorityAsc(c.getId()).stream()
                .map(r -> new RuleSnapshot(r.getId(), r.getType(), r.getAttribute(), r.getOperator(),
                        List.copyOf(r.getRuleValues()), r.isServeEnabled()))
                .toList();
        return SnapshotLookup.found(new FlagSnapshot(f.getId(), f.getFlagKey(), f.isArchived(),
                c.isEnabled(), c.getRolloutPercentage(), ruleSnapshots));
    }
}

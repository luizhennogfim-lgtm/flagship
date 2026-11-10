package io.flagship.evaluation;

import java.util.List;
import java.util.UUID;

/**
 * Everything needed to evaluate one flag in one environment. It is a plain immutable value
 * on purpose: Stage 5 stores exactly this object in Redis.
 */
public record FlagSnapshot(
        UUID flagId,
        String flagKey,
        boolean archived,
        boolean enabled,
        int rolloutPercentage,
        List<RuleSnapshot> rules) {
}

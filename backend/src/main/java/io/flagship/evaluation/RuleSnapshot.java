package io.flagship.evaluation;

import java.util.List;
import java.util.UUID;

import io.flagship.flag.RuleOperator;
import io.flagship.flag.RuleType;

/** Immutable copy of a rule, safe to cache. */
public record RuleSnapshot(
        UUID id,
        RuleType type,
        String attribute,
        RuleOperator operator,
        List<String> values,
        boolean serveEnabled) {
}

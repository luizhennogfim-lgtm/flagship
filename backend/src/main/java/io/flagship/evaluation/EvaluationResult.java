package io.flagship.evaluation;

import java.util.UUID;

/** ruleId is only filled when reason is RULE_MATCH. */
public record EvaluationResult(boolean enabled, EvaluationReason reason, UUID ruleId) {

    public static EvaluationResult off(EvaluationReason reason) {
        return new EvaluationResult(false, reason, null);
    }
}

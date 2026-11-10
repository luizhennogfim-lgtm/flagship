package io.flagship.evaluation;

import org.springframework.stereotype.Component;

/**
 * The decision logic, with no database and no HTTP: easy to test and safe to run on cached data.
 *
 * <p>Order of precedence:
 * <ol>
 *   <li>archived flag: OFF</li>
 *   <li>flag switched OFF in this environment: OFF (the kill switch beats everything below)</li>
 *   <li>rules, by priority: the first one that matches decides</li>
 *   <li>percentage rollout, using deterministic bucketing</li>
 * </ol>
 */
@Component
public class FlagEvaluator {

    public EvaluationResult evaluate(FlagSnapshot flag, EvaluationContext context) {
        if (flag.archived()) {
            return EvaluationResult.off(EvaluationReason.FLAG_ARCHIVED);
        }
        if (!flag.enabled()) {
            return EvaluationResult.off(EvaluationReason.FLAG_DISABLED);
        }
        for (RuleSnapshot rule : flag.rules()) {
            if (RuleMatcher.matches(rule, context)) {
                return new EvaluationResult(rule.serveEnabled(), EvaluationReason.RULE_MATCH, rule.id());
            }
        }
        boolean inside = Bucketing.bucket(flag.flagKey(), context.userId()) < flag.rolloutPercentage();
        return new EvaluationResult(inside,
                inside ? EvaluationReason.PERCENTAGE_ROLLOUT : EvaluationReason.NOT_IN_ROLLOUT, null);
    }
}

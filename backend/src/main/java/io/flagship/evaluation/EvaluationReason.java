package io.flagship.evaluation;

/** Why an evaluation returned its answer. Useful for debugging "why did this user (not) get the feature?". */
public enum EvaluationReason {
    FLAG_NOT_FOUND,
    ENVIRONMENT_NOT_FOUND,
    FLAG_ARCHIVED,
    FLAG_DISABLED,
    RULE_MATCH,
    PERCENTAGE_ROLLOUT,
    NOT_IN_ROLLOUT
}

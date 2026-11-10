package io.flagship.evaluation;

/** variant is "on" or "off": this version of Flagship has boolean flags only (no multivariate flags). */
public record EvaluationResponse(boolean enabled, EvaluationReason reason, String variant) {

    static EvaluationResponse from(EvaluationResult result) {
        return new EvaluationResponse(result.enabled(), result.reason(), result.enabled() ? "on" : "off");
    }
}

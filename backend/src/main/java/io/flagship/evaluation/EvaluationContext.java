package io.flagship.evaluation;

import java.util.Map;
import java.util.Set;

/** Who is asking: the user id, the groups the user belongs to and any attributes the application sends. */
public record EvaluationContext(String userId, Set<String> groups, Map<String, String> attributes) {

    public EvaluationContext {
        groups = groups == null ? Set.of() : Set.copyOf(groups);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static EvaluationContext ofUser(String userId) {
        return new EvaluationContext(userId, Set.of(), Map.of());
    }
}

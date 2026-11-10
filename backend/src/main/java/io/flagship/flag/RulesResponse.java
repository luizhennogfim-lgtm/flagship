package io.flagship.flag;

import java.util.List;

/** The version is the same one used by the flag state of that environment (optimistic locking). */
public record RulesResponse(long version, List<RuleResponse> rules) {
}

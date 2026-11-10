package io.flagship.flag;

import java.util.List;
import java.util.UUID;

public record RuleResponse(
        UUID id,
        int priority,
        RuleType type,
        String attribute,
        RuleOperator operator,
        List<String> values,
        boolean serve,
        String description) {

    static RuleResponse from(FlagRule rule) {
        return new RuleResponse(rule.getId(), rule.getPriority(), rule.getType(), rule.getAttribute(),
                rule.getOperator(), rule.getRuleValues(), rule.isServeEnabled(), rule.getDescription());
    }
}

package io.flagship.evaluation;

import java.util.List;

/** Decides whether one rule matches the person being evaluated. */
final class RuleMatcher {

    private RuleMatcher() {
    }

    static boolean matches(RuleSnapshot rule, EvaluationContext context) {
        return switch (rule.type()) {
            case USER_LIST -> rule.values().contains(context.userId());
            case GROUP -> rule.values().stream().anyMatch(context.groups()::contains);
            case ATTRIBUTE -> matchesAttribute(rule, context.attributes().get(rule.attribute()));
        };
    }

    /** A missing attribute never matches, not even for NOT_EQUALS / NOT_IN: "unknown" is not "different". */
    private static boolean matchesAttribute(RuleSnapshot rule, String actual) {
        if (actual == null || rule.operator() == null) {
            return false;
        }
        List<String> values = rule.values();
        String expected = values.get(0);
        return switch (rule.operator()) {
            case EQUALS -> expected.equals(actual);
            case NOT_EQUALS -> !expected.equals(actual);
            case IN -> values.contains(actual);
            case NOT_IN -> !values.contains(actual);
            case CONTAINS -> actual.contains(expected);
            case STARTS_WITH -> actual.startsWith(expected);
            case ENDS_WITH -> actual.endsWith(expected);
            case GREATER_THAN -> compareNumbers(actual, expected) > 0;
            case LESS_THAN -> compareNumbers(actual, expected) < 0;
        };
    }

    /** Returns 0 when either side is not a number, so GREATER_THAN / LESS_THAN are both false. */
    private static int compareNumbers(String actual, String expected) {
        try {
            return Double.compare(Double.parseDouble(actual), Double.parseDouble(expected));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}

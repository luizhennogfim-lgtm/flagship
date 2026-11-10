package io.flagship.flag;

public enum RuleOperator {
    EQUALS, NOT_EQUALS, IN, NOT_IN, CONTAINS, STARTS_WITH, ENDS_WITH, GREATER_THAN, LESS_THAN;

    public boolean isNumeric() {
        return this == GREATER_THAN || this == LESS_THAN;
    }

    public boolean acceptsManyValues() {
        return this == IN || this == NOT_IN;
    }
}

package io.flagship.flag;

public enum RuleType {
    USER_LIST,   // the user id is in a list
    GROUP,       // the user belongs to one of the listed groups
    ATTRIBUTE    // an attribute of the user satisfies a condition
}

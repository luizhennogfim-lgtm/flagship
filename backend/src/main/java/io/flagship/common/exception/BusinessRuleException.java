package io.flagship.common.exception;

/** Thrown when a request is well-formed but violates a business rule. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}

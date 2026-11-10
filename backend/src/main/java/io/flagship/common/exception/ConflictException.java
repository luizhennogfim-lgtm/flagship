package io.flagship.common.exception;

/** Thrown when a request clashes with existing data (duplicate email, duplicate name...). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

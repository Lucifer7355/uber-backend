package com.uber.backend.common.exception;

public final class ValidationException extends DomainException {

    public ValidationException(String message) {
        super("VALIDATION", message);
    }
}

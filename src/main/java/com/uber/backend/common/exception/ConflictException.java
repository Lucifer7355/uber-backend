package com.uber.backend.common.exception;

public final class ConflictException extends DomainException {

    public ConflictException(String message) {
        super("CONFLICT", message);
    }
}

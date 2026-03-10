package org.fineract.iso20022.exception;

import lombok.Getter;

@Getter
public class IdempotencyException extends RuntimeException {

    private final String existingResult;

    public IdempotencyException(String message, String existingResult) {
        super(message);
        this.existingResult = existingResult;
    }
}

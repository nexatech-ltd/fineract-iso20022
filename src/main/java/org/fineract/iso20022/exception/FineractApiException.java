package org.fineract.iso20022.exception;

import lombok.Getter;

@Getter
public class FineractApiException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public FineractApiException(String message, int statusCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public FineractApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.responseBody = null;
    }
}

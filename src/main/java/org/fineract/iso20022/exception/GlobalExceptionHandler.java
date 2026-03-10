package org.fineract.iso20022.exception;

import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MessageParsingException.class)
    public ResponseEntity<Map<String, Object>> handleMessageParsing(MessageParsingException ex) {
        log.error("Message parsing error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.BAD_REQUEST, "MESSAGE_PARSING_ERROR",
                sanitizeMessage(ex.getMessage()));
    }

    @ExceptionHandler(FineractApiException.class)
    public ResponseEntity<Map<String, Object>> handleFineractApi(FineractApiException ex) {
        log.error("Fineract API error: {} (status={})", ex.getMessage(), ex.getStatusCode(), ex);
        return buildResponse(HttpStatus.BAD_GATEWAY, "FINERACT_API_ERROR",
                "Upstream financial service error. Please retry or contact support.");
    }

    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentProcessing(PaymentProcessingException ex) {
        log.error("Payment processing error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_PROCESSING_ERROR",
                sanitizeMessage(ex.getMessage()));
    }

    @ExceptionHandler(IdempotencyException.class)
    public ResponseEntity<Map<String, Object>> handleIdempotency(IdempotencyException ex) {
        log.info("Idempotent request detected: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "DUPLICATE_REQUEST",
                "This request has already been processed.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        String errors = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Constraint violation");
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", errors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
                sanitizeMessage(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred");
    }

    private String sanitizeMessage(String message) {
        if (message == null) return "Processing error";
        return message
                .replaceAll("(?i)(password|secret|key|token)\\s*[:=]\\s*\\S+", "[REDACTED]")
                .replaceAll("\\b\\d{10,}\\b", "[REDACTED]");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}

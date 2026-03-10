package org.fineract.iso20022.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.fineract.iso20022.model.dto.AuditLogEntry;
import org.fineract.iso20022.model.dto.PaymentMessageSummary;
import org.fineract.iso20022.model.entity.PaymentMessage;
import org.fineract.iso20022.model.entity.SystemAuditLog;
import org.fineract.iso20022.model.enums.MessageDirection;
import org.fineract.iso20022.model.enums.MessageStatus;
import org.fineract.iso20022.repository.MessageAuditLogRepository;
import org.fineract.iso20022.repository.PaymentMessageRepository;
import org.fineract.iso20022.repository.SystemAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
@Validated
@Tag(name = "Messages", description = "Message history and audit endpoints")
public class MessageController {

    private final PaymentMessageRepository paymentMessageRepository;
    private final MessageAuditLogRepository auditLogRepository;
    private final SystemAuditLogRepository systemAuditLogRepository;

    @GetMapping
    @Operation(summary = "List all payment messages with pagination")
    public ResponseEntity<Page<PaymentMessageSummary>> listMessages(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) MessageDirection direction) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PaymentMessage> messages;
        if (direction != null) {
            messages = paymentMessageRepository.findByDirection(direction, pageRequest);
        } else {
            messages = paymentMessageRepository.findAll(pageRequest);
        }
        return ResponseEntity.ok(messages.map(PaymentMessageSummary::fromEntity));
    }

    @GetMapping("/{messageId}")
    @Operation(summary = "Get message by ID")
    public ResponseEntity<PaymentMessageSummary> getMessage(
            @PathVariable @Size(max = 64) String messageId) {
        return paymentMessageRepository.findByMessageId(messageId)
                .map(pm -> ResponseEntity.ok(PaymentMessageSummary.fromEntity(pm)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "List messages by status")
    public ResponseEntity<List<PaymentMessageSummary>> getByStatus(@PathVariable MessageStatus status) {
        return ResponseEntity.ok(paymentMessageRepository.findByStatus(status).stream()
                .map(PaymentMessageSummary::fromEntity).toList());
    }

    @GetMapping("/account/{accountId}")
    @Operation(summary = "List messages by account")
    public ResponseEntity<List<PaymentMessageSummary>> getByAccount(
            @PathVariable @Size(max = 64) String accountId) {
        return ResponseEntity.ok(paymentMessageRepository.findByAccount(accountId).stream()
                .map(PaymentMessageSummary::fromEntity).toList());
    }

    @GetMapping("/range")
    @Operation(summary = "List messages by date range and status")
    public ResponseEntity<List<PaymentMessageSummary>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam MessageStatus status) {
        return ResponseEntity.ok(paymentMessageRepository.findByDateRangeAndStatus(from, to, status).stream()
                .map(PaymentMessageSummary::fromEntity).toList());
    }

    @GetMapping("/{messageId}/audit")
    @Operation(summary = "Get audit trail for a message")
    public ResponseEntity<List<AuditLogEntry>> getAuditTrail(
            @PathVariable @Size(max = 64) String messageId) {
        return paymentMessageRepository.findByMessageId(messageId)
                .map(pm -> ResponseEntity.ok(
                        auditLogRepository.findByPaymentMessageIdOrderByCreatedAtAsc(pm.getId()).stream()
                                .map(AuditLogEntry::fromMessageAudit).toList()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/system-audit")
    @Operation(summary = "List system audit events with pagination")
    public ResponseEntity<Page<AuditLogEntry>> listSystemAudit(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String sourceComponent) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SystemAuditLog> result;
        if (eventType != null) {
            result = systemAuditLogRepository.findByEventType(eventType, pageRequest);
        } else if (sourceComponent != null) {
            result = systemAuditLogRepository.findBySourceComponent(sourceComponent, pageRequest);
        } else {
            result = systemAuditLogRepository.findAll(pageRequest);
        }
        return ResponseEntity.ok(result.map(AuditLogEntry::fromSystemAudit));
    }

    @GetMapping("/system-audit/resource/{resourceType}/{resourceId}")
    @Operation(summary = "Get system audit trail for a specific resource")
    public ResponseEntity<List<AuditLogEntry>> getResourceAudit(
            @PathVariable @Size(max = 40) String resourceType,
            @PathVariable @Size(max = 255) String resourceId) {
        return ResponseEntity.ok(
                systemAuditLogRepository.findByResourceTypeAndResourceId(resourceType, resourceId).stream()
                        .map(AuditLogEntry::fromSystemAudit).toList());
    }

    @GetMapping("/system-audit/range")
    @Operation(summary = "List system audit events by date range")
    public ResponseEntity<Page<AuditLogEntry>> getSystemAuditByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(systemAuditLogRepository.findByCreatedAtBetween(from, to, pageRequest)
                .map(AuditLogEntry::fromSystemAudit));
    }
}

package org.fineract.iso20022.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.model.entity.MessageAuditLog;
import org.fineract.iso20022.model.entity.PaymentMessage;
import org.fineract.iso20022.model.entity.SystemAuditLog;
import org.fineract.iso20022.repository.MessageAuditLogRepository;
import org.fineract.iso20022.repository.SystemAuditLogRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final MessageAuditLogRepository auditLogRepository;
    private final SystemAuditLogRepository systemAuditLogRepository;

    // ── Message-level audit (tied to a PaymentMessage) ───────────────────

    @Async("messageProcessingExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(PaymentMessage paymentMessage, String action, String details) {
        try {
            MessageAuditLog auditLog = MessageAuditLog.builder()
                    .paymentMessage(paymentMessage)
                    .action(action)
                    .details(details)
                    .build();
            auditLogRepository.save(auditLog);
            log.debug("Audit log: message={}, action={}", paymentMessage.getMessageId(), action);
        } catch (Exception e) {
            log.error("Failed to write audit log for message {}: {}",
                    paymentMessage.getMessageId(), e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logActionSync(PaymentMessage paymentMessage, String action, String details) {
        MessageAuditLog auditLog = MessageAuditLog.builder()
                .paymentMessage(paymentMessage)
                .action(action)
                .details(details)
                .build();
        auditLogRepository.save(auditLog);
    }

    // ── System-level audit (not tied to a specific PaymentMessage) ───────

    @Async("messageProcessingExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSystemEvent(String eventType, String eventAction, String source,
                               String resourceType, String resourceId, String details) {
        try {
            SystemAuditLog entry = SystemAuditLog.builder()
                    .eventType(eventType)
                    .eventAction(eventAction)
                    .sourceComponent(source)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .details(details)
                    .build();
            systemAuditLogRepository.save(entry);
            log.debug("System audit: type={}, action={}, resource={}:{}",
                    eventType, eventAction, resourceType, resourceId);
        } catch (Exception e) {
            log.error("Failed to write system audit log: {}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSystemEventSync(String eventType, String eventAction, String source,
                                   String resourceType, String resourceId, String details) {
        SystemAuditLog entry = SystemAuditLog.builder()
                .eventType(eventType)
                .eventAction(eventAction)
                .sourceComponent(source)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .details(details)
                .build();
        systemAuditLogRepository.save(entry);
    }

    // ── Convenience methods for common event types ───────────────────────

    public void logFineractApiCall(String method, String endpoint, String result, String details) {
        logSystemEvent("FINERACT_API", result, "FineractClientService",
                "api_call", method + " " + endpoint, details);
    }

    public void logKafkaPublish(String topic, String messageId, String messageType) {
        logSystemEvent("KAFKA_PUBLISH", "SENT", "Iso20022MessageProducer",
                "kafka_message", messageId, "topic=" + topic + ", type=" + messageType);
    }

    public void logKafkaConsume(String topic, String eventType, String details) {
        logSystemEvent("KAFKA_CONSUME", "RECEIVED", "Iso20022MessageConsumer",
                "kafka_message", eventType, "topic=" + topic + "; " + details);
    }

    public void logFineractEventReceived(String eventType, String format, String details) {
        logSystemEvent("FINERACT_EVENT", "RECEIVED", "FineractEventConsumer",
                "fineract_event", eventType, "format=" + format + "; " + details);
    }

    public void logCacheOperation(String operation, String cacheKey, String details) {
        logSystemEvent("CACHE", operation, "CacheService",
                "cache_entry", cacheKey, details);
    }

    public void logIdempotencyCheck(String action, String idempotencyKey, String details) {
        logSystemEvent("IDEMPOTENCY", action, "IdempotencyService",
                "idempotency_key", idempotencyKey, details);
    }

    public void logAccountResolution(String action, String identifier, String details) {
        logSystemEvent("ACCOUNT_RESOLUTION", action, "AccountResolutionService",
                "account", identifier, details);
    }

    public void logMandateLifecycle(String action, String mandateId, String details) {
        logSystemEvent("MANDATE_LIFECYCLE", action, "DirectDebitService",
                "mandate", mandateId, details);
    }

    public void logXmlValidation(String action, String messageType, String details) {
        logSystemEvent("XML_VALIDATION", action, "IsoMessageValidator",
                "iso20022_message", messageType, details);
    }

    public void logAccountManagement(String action, String accountId, String details) {
        logSystemEvent("ACCOUNT_MANAGEMENT", action, "AccountManagementService",
                "savings_account", accountId, details);
    }
}

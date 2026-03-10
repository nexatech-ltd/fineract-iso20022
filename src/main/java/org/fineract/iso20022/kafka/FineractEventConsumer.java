package org.fineract.iso20022.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.fineract.iso20022.mapper.Camt054Mapper;
import org.fineract.iso20022.model.entity.PaymentMessage;
import org.fineract.iso20022.model.enums.MessageDirection;
import org.fineract.iso20022.model.enums.MessageStatus;
import org.fineract.iso20022.repository.PaymentMessageRepository;
import org.fineract.iso20022.service.AuditService;
import org.fineract.iso20022.service.CacheService;
import org.fineract.iso20022.util.IdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "iso20022.fineract-events.enabled", havingValue = "true", matchIfMissing = false)
public class FineractEventConsumer {

    private final ObjectMapper objectMapper;
    private final CacheService cacheService;
    private final Camt054Mapper camt054Mapper;
    private final Iso20022MessageProducer producer;
    private final PaymentMessageRepository paymentMessageRepository;
    private final AuditService auditService;
    private final FineractAvroDeserializer avroDeserializer;

    @Value("${iso20022.fineract-events.format:json}")
    private String eventFormat;

    @KafkaListener(
            topics = "${iso20022.fineract-events.topic:fineract-external-events}",
            groupId = "iso20022-fineract-events",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeFineractEvent(@Payload byte[] eventPayload) {
        try {
            if ("avro".equalsIgnoreCase(eventFormat)) {
                processAvroEvent(eventPayload);
            } else {
                processJsonEvent(new String(eventPayload, java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            log.error("Failed to process Fineract event: {}", e.getMessage(), e);
        }
    }

    private void processAvroEvent(byte[] avroBytes) {
        FineractAvroDeserializer.FineractExternalEvent event = avroDeserializer.deserialize(avroBytes);
        String type = event.type();
        String category = event.category() != null ? event.category() : "";
        auditService.logFineractEventReceived(type, "avro", "category=" + category + ", dataschema=" + event.dataschema());

        log.info("Received Fineract Avro event: type={}, category={}, dataschema={}", 
                type, category, event.dataschema());

        GenericRecord data = event.data();
        if (data == null) {
            log.debug("No inner data decoded for event type={}", type);
            return;
        }

        if (type.contains("SavingsAccountTransaction") || 
                (event.dataschema() != null && event.dataschema().contains("SavingsAccountTransaction"))) {
            handleAvroSavingsTransaction(type, data);
        } else if (type.contains("LoanTransaction") || 
                (event.dataschema() != null && event.dataschema().contains("LoanTransaction"))) {
            handleAvroLoanTransaction(type, data);
        } else if (type.contains("SavingsAccount") || type.contains("LoanAccount")) {
            handleAvroAccountChange(data);
        }
    }

    private void handleAvroSavingsTransaction(String eventType, GenericRecord data) {
        Long accountId = FineractAvroDeserializer.extractLong(data, "accountId");
        Long txnId = FineractAvroDeserializer.extractLong(data, "id");
        BigDecimal amount = FineractAvroDeserializer.extractBigDecimal(data, "amount");
        
        GenericRecord currencyRec = FineractAvroDeserializer.extractRecord(data, "currency");
        String currency = currencyRec != null ? FineractAvroDeserializer.extractString(currencyRec, "code") : "USD";

        GenericRecord txnTypeRec = FineractAvroDeserializer.extractRecord(data, "transactionType");
        boolean isCredit = false;
        if (txnTypeRec != null) {
            Boolean deposit = FineractAvroDeserializer.extractBoolean(txnTypeRec, "deposit");
            isCredit = Boolean.TRUE.equals(deposit);
        }

        String accountIdStr = accountId != null ? accountId.toString() : "";
        String txnIdStr = txnId != null ? txnId.toString() : "";

        if (!accountIdStr.isEmpty()) {
            cacheService.evictAccountCache(accountIdStr);
        }

        publishNotification(accountIdStr, txnIdStr, amount != null ? amount : BigDecimal.ZERO, 
                currency, isCredit, "savings");
    }

    private void handleAvroLoanTransaction(String eventType, GenericRecord data) {
        Long loanId = FineractAvroDeserializer.extractLong(data, "loanId");
        Long txnId = FineractAvroDeserializer.extractLong(data, "id");
        BigDecimal amount = FineractAvroDeserializer.extractBigDecimal(data, "amount");
        
        GenericRecord currencyRec = FineractAvroDeserializer.extractRecord(data, "currency");
        String currency = currencyRec != null ? FineractAvroDeserializer.extractString(currencyRec, "code") : "USD";

        GenericRecord txnTypeRec = FineractAvroDeserializer.extractRecord(data, "type");
        boolean isCredit = false;
        if (txnTypeRec != null) {
            Boolean disbursement = FineractAvroDeserializer.extractBoolean(txnTypeRec, "disbursement");
            isCredit = Boolean.TRUE.equals(disbursement);
        }

        String loanIdStr = loanId != null ? loanId.toString() : "";
        String txnIdStr = txnId != null ? txnId.toString() : "";

        publishNotification(loanIdStr, txnIdStr, amount != null ? amount : BigDecimal.ZERO, 
                currency, isCredit, "loan");
    }

    private void handleAvroAccountChange(GenericRecord data) {
        Long accountId = FineractAvroDeserializer.extractLong(data, "id");
        String accountIdStr = accountId != null ? accountId.toString() : "";
        String externalId = FineractAvroDeserializer.extractString(data, "externalId");
        
        if (!accountIdStr.isEmpty()) {
            cacheService.evictAccountCache(accountIdStr);
            auditService.logCacheOperation("EVICTED", accountIdStr, "Fineract event triggered cache eviction");
            log.info("Evicted cache for account {} due to Fineract Avro event", accountIdStr);
        }
        if (externalId != null && !externalId.isEmpty()) {
            cacheService.evictAccountCache(externalId);
        }
    }

    private void processJsonEvent(String eventPayload) {
        try {
            JsonNode event = objectMapper.readTree(eventPayload);
            String type = event.path("type").asText("");
            String action = event.path("action").asText("");
            JsonNode body = event.path("body");
            auditService.logFineractEventReceived(type, "json", "action=" + action);

            log.info("Received Fineract JSON event: type={}, action={}", type, action);

            if (type.contains("SavingsAccountTransaction")) {
                handleJsonSavingsTransaction(action, body);
            } else if (type.contains("LoanTransaction")) {
                handleJsonLoanTransaction(action, body);
            } else if (type.contains("SavingsAccount") || type.contains("LoanAccount")) {
                handleJsonAccountChange(body);
            }
        } catch (Exception e) {
            log.error("Failed to parse JSON event: {}", e.getMessage(), e);
        }
    }

    private void handleJsonSavingsTransaction(String action, JsonNode body) {
        String accountId = body.path("savingsAccountId").asText(body.path("savingsId").asText(""));
        String txnId = body.path("resourceId").asText(body.path("transactionId").asText(""));
        BigDecimal amount = body.has("amount") ? body.get("amount").decimalValue() : BigDecimal.ZERO;
        String currency = body.path("currency").path("code").asText("USD");

        cacheService.evictAccountCache(accountId);
        boolean isCredit = action.contains("DEPOSIT") || action.contains("deposit");
        publishNotification(accountId, txnId, amount, currency, isCredit, "savings");
    }

    private void handleJsonLoanTransaction(String action, JsonNode body) {
        String loanId = body.path("loanId").asText("");
        String txnId = body.path("resourceId").asText(body.path("transactionId").asText(""));
        BigDecimal amount = body.has("amount") ? body.get("amount").decimalValue() : BigDecimal.ZERO;
        String currency = body.path("currency").path("code").asText("USD");

        boolean isCredit = action.contains("DISBURSEMENT") || action.contains("disbursement");
        publishNotification(loanId, txnId, amount, currency, isCredit, "loan");
    }

    private void handleJsonAccountChange(JsonNode body) {
        String accountId = body.path("savingsAccountId").asText(
                body.path("savingsId").asText(body.path("accountId").asText("")));
        if (!accountId.isEmpty()) {
            cacheService.evictAccountCache(accountId);
            auditService.logCacheOperation("EVICTED", accountId, "Fineract event triggered cache eviction");
            log.info("Evicted cache for account {} due to Fineract JSON event", accountId);
        }
    }

    private void publishNotification(String accountId, String txnId, BigDecimal amount, 
                                      String currency, boolean isCredit, String source) {
        PaymentMessage pm = PaymentMessage.builder()
                .messageId(IdGenerator.generateMessageId())
                .messageType("fineract." + source + "." + (isCredit ? "credit" : "debit"))
                .direction(MessageDirection.INBOUND)
                .status(MessageStatus.COMPLETED)
                .rawXml("")
                .debtorAccount(isCredit ? null : accountId)
                .creditorAccount(isCredit ? accountId : null)
                .amount(amount)
                .currency(currency)
                .fineractTransactionId(txnId)
                .build();
        pm = paymentMessageRepository.save(pm);

        String notificationXml = camt054Mapper.buildNotification(pm, isCredit);

        PaymentMessage notifMsg = PaymentMessage.builder()
                .messageId(IdGenerator.generateMessageId())
                .messageType("camt.054")
                .direction(MessageDirection.OUTBOUND)
                .status(MessageStatus.COMPLETED)
                .rawXml(notificationXml)
                .creditorAccount(isCredit ? accountId : null)
                .debtorAccount(isCredit ? null : accountId)
                .amount(amount)
                .currency(currency)
                .build();
        notifMsg = paymentMessageRepository.save(notifMsg);
        auditService.logAction(notifMsg, "GENERATED", "Auto camt.054 from Fineract " + source + " event");

        producer.publishOutbound(notifMsg.getMessageId(), "camt.054", notificationXml);
        log.info("Generated camt.054 from Fineract {} event: account={}, txn={}", source, accountId, txnId);
    }
}

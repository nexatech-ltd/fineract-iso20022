package org.fineract.iso20022.kafka;

import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.fineract.iso20022.service.AuditService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class Iso20022MessageProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AuditService auditService;

    @Value("${iso20022.kafka.outbound-topic}")
    private String outboundTopic;

    @Value("${iso20022.kafka.status-topic}")
    private String statusTopic;

    @Value("${iso20022.kafka.dlq-topic}")
    private String dlqTopic;

    /**
     * Publish an outbound ISO 20022 XML message (e.g., camt.053, camt.054).
     */
    public void publishOutbound(String messageId, String messageType, String xmlPayload) {
        ProducerRecord<String, String> record = new ProducerRecord<>(outboundTopic, messageId, xmlPayload);
        record.headers().add(new RecordHeader("messageType", messageType.getBytes()));
        record.headers().add(new RecordHeader("messageId", messageId.getBytes()));

        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record);
        auditService.logKafkaPublish(outboundTopic, messageId, messageType);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish outbound message {}: {}", messageId, ex.getMessage());
            } else {
                log.info("Published outbound {} message {} to partition {} offset {}",
                        messageType, messageId,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    /**
     * Publish a pacs.002 status report.
     */
    public void publishStatusReport(String messageId, String statusXml) {
        ProducerRecord<String, String> record = new ProducerRecord<>(statusTopic, messageId, statusXml);
        record.headers().add(new RecordHeader("messageType", "pacs.002".getBytes()));

        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record);
        auditService.logKafkaPublish(statusTopic, messageId, "pacs.002");
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish status report for {}: {}", messageId, ex.getMessage());
            } else {
                log.info("Published pacs.002 status for {} to partition {} offset {}",
                        messageId,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    /**
     * Publish a message to the Dead Letter Queue.
     */
    public void publishToDlq(String messageId, String originalMessage, String errorReason) {
        ProducerRecord<String, String> record = new ProducerRecord<>(dlqTopic, messageId, originalMessage);
        record.headers().add(new RecordHeader("error", errorReason.getBytes()));
        record.headers().add(new RecordHeader("originalMessageId", messageId.getBytes()));

        auditService.logKafkaPublish(dlqTopic, messageId, "DLQ");
        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish to DLQ for {}: {}", messageId, ex.getMessage());
            } else {
                log.warn("Message {} sent to DLQ: {}", messageId, errorReason);
            }
        });
    }
}

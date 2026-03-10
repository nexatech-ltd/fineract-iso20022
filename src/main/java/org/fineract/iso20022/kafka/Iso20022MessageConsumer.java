package org.fineract.iso20022.kafka;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.model.dto.PaymentInitiationRequest;
import org.fineract.iso20022.model.dto.PaymentStatusResponse;
import org.fineract.iso20022.service.AuditService;
import org.fineract.iso20022.service.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class Iso20022MessageConsumer {

    private final PaymentService paymentService;
    private final Iso20022MessageProducer producer;
    private final AuditService auditService;

    @KafkaListener(
            topics = "${iso20022.kafka.inbound-topic}",
            groupId = "iso20022-adapter",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeInboundMessage(
            @Payload String xmlMessage,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received inbound message: key={}, partition={}, offset={}", key, partition, offset);
        auditService.logKafkaConsume("iso20022.inbound", "ISO20022_MESSAGE", "Received inbound message");

        try {
            PaymentInitiationRequest request = PaymentInitiationRequest.builder()
                    .xmlMessage(xmlMessage)
                    .idempotencyKey("kafka:" + key + ":" + partition + ":" + offset)
                    .build();

            List<PaymentStatusResponse> responses = paymentService.processPayment(request);

            for (PaymentStatusResponse response : responses) {
                if (response.getStatusXml() != null) {
                    producer.publishStatusReport(response.getMessageId(), response.getStatusXml());
                }
            }

            acknowledgment.acknowledge();
            log.info("Successfully processed inbound message: key={}, {} instructions", key, responses.size());

        } catch (Exception e) {
            log.error("Failed to process inbound message: key={}, error={}", key, e.getMessage(), e);
            producer.publishToDlq(key, xmlMessage, e.getMessage());
            acknowledgment.acknowledge();
        }
    }
}

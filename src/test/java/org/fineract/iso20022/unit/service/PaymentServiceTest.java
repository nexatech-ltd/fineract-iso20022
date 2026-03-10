package org.fineract.iso20022.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.fineract.iso20022.exception.IdempotencyException;
import org.fineract.iso20022.mapper.Pacs002Mapper;
import org.fineract.iso20022.model.dto.PaymentInitiationRequest;
import org.fineract.iso20022.model.dto.PaymentStatusResponse;
import org.fineract.iso20022.model.entity.PaymentMessage;
import org.fineract.iso20022.model.enums.MessageStatus;
import org.fineract.iso20022.repository.PaymentMessageRepository;
import org.fineract.iso20022.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private Iso20022MessageService messageService;
    @Mock private FineractClientService fineractClient;
    @Mock private AccountResolutionService accountResolutionService;
    @Mock private DirectDebitService directDebitService;
    @Mock private ReversalService reversalService;
    @Mock private StatementService statementService;
    @Mock private AccountManagementService accountManagementService;
    @Mock private PaymentMessageRepository paymentMessageRepository;
    @Mock private IdempotencyService idempotencyService;
    @Mock private AuditService auditService;
    @Mock private Pacs002Mapper pacs002Mapper;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                messageService, fineractClient, accountResolutionService,
                directDebitService, reversalService, statementService,
                accountManagementService, paymentMessageRepository, idempotencyService,
                auditService, pacs002Mapper);
    }

    @Test
    void shouldProcessPain001Payment() throws Exception {
        String xml = loadSampleXml("sample-pain001.xml");
        PaymentInitiationRequest request = PaymentInitiationRequest.builder()
                .xmlMessage(xml)
                .build();

        var instruction = org.fineract.iso20022.model.dto.InternalPaymentInstruction.builder()
                .messageId("MSG-TEST-001")
                .endToEndId("E2E-001")
                .debtorName("John Doe")
                .debtorAccountIban("DE89370400440532013000")
                .creditorName("Jane Smith")
                .creditorAccountIban("FR7630006000011234567890189")
                .amount(new BigDecimal("1000.00"))
                .currency("EUR")
                .originalMessageType("pain.001")
                .build();

        when(messageService.parseInbound(xml)).thenReturn(
                new Iso20022MessageService.ParsedMessage(
                        null, "pain.001.001.11",
                        org.fineract.iso20022.model.enums.Iso20022MessageType.PAIN_001,
                        List.of(instruction)));

        when(paymentMessageRepository.save(any(PaymentMessage.class)))
                .thenAnswer(inv -> {
                    PaymentMessage pm = inv.getArgument(0);
                    pm.setId(1L);
                    return pm;
                });

        when(accountResolutionService.resolveToFineractId(eq("DE89370400440532013000"), any(), any()))
                .thenReturn("ACC-001");
        when(accountResolutionService.resolveToFineractId(eq("FR7630006000011234567890189"), any(), any()))
                .thenReturn("ACC-002");
        when(fineractClient.executeTransfer(any())).thenReturn("TXN-12345");

        when(pacs002Mapper.buildStatusReport(any(), any(), any())).thenReturn("<pacs002/>");
        when(pacs002Mapper.toStatusResponse(any(), any())).thenReturn(
                PaymentStatusResponse.builder()
                        .messageId("MSG-TEST-001")
                        .status("COMPLETED")
                        .fineractTransactionId("TXN-12345")
                        .statusXml("<pacs002/>")
                        .build());

        List<PaymentStatusResponse> responses = paymentService.processPayment(request);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getStatus()).isEqualTo("COMPLETED");
        verify(fineractClient).executeTransfer(any());
        verify(auditService, atLeast(1)).logAction(any(), anyString(), anyString());
    }

    @Test
    void shouldRejectDuplicateIdempotencyKey() {
        PaymentInitiationRequest request = PaymentInitiationRequest.builder()
                .xmlMessage("<xml/>")
                .idempotencyKey("duplicate-key")
                .build();

        when(idempotencyService.checkDuplicate("duplicate-key")).thenReturn("ALREADY_PROCESSED");

        assertThatThrownBy(() -> paymentService.processPayment(request))
                .isInstanceOf(IdempotencyException.class);
    }

    @Test
    void shouldGetPaymentStatus() {
        PaymentMessage pm = PaymentMessage.builder()
                .id(1L)
                .messageId("STATUS-001")
                .status(MessageStatus.COMPLETED)
                .fineractTransactionId("TXN-001")
                .build();

        when(paymentMessageRepository.findByMessageId("STATUS-001")).thenReturn(Optional.of(pm));
        when(pacs002Mapper.buildStatusReport(any(), any(), any())).thenReturn("<status/>");
        when(pacs002Mapper.toStatusResponse(any(), any())).thenReturn(
                PaymentStatusResponse.builder()
                        .status("COMPLETED")
                        .fineractTransactionId("TXN-001")
                        .build());

        PaymentStatusResponse response = paymentService.getPaymentStatus("STATUS-001");

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getFineractTransactionId()).isEqualTo("TXN-001");
    }

    private String loadSampleXml(String filename) {
        try (var is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

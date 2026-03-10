package org.fineract.iso20022.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.exception.IdempotencyException;
import org.fineract.iso20022.exception.PaymentProcessingException;
import org.fineract.iso20022.kafka.Iso20022MessageProducer;
import org.fineract.iso20022.mapper.Pacs002Mapper;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.fineract.iso20022.model.dto.PaymentInitiationRequest;
import org.fineract.iso20022.model.dto.PaymentStatusResponse;
import org.fineract.iso20022.model.entity.PaymentMessage;
import org.fineract.iso20022.model.enums.MessageDirection;
import org.fineract.iso20022.model.enums.MessageStatus;
import org.fineract.iso20022.model.enums.OperationType;
import org.fineract.iso20022.repository.PaymentMessageRepository;
import org.fineract.iso20022.service.Iso20022MessageService.ParsedMessage;
import org.fineract.iso20022.util.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final Iso20022MessageService messageService;
    private final FineractClientService fineractClient;
    private final AccountResolutionService accountResolution;
    private final DirectDebitService directDebitService;
    private final ReversalService reversalService;
    private final StatementService statementService;
    private final AccountManagementService accountManagementService;
    private final PaymentMessageRepository paymentMessageRepository;
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final Pacs002Mapper pacs002Mapper;
    private final Iso20022MessageProducer kafkaProducer;

    @Transactional
    public List<PaymentStatusResponse> processPayment(PaymentInitiationRequest request) {
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey != null) {
            String existing = idempotencyService.checkDuplicate(idempotencyKey);
            if (existing != null) {
                auditService.logIdempotencyCheck("DUPLICATE_DETECTED", idempotencyKey, "Existing result: " + existing);
                throw new IdempotencyException("Duplicate request detected", existing);
            }
            if (!idempotencyService.tryLock(idempotencyKey)) {
                throw new IdempotencyException("Request is already being processed", null);
            }
        }

        try {
            ParsedMessage parsed = messageService.parseInbound(request.getXmlMessage());
            auditService.logXmlValidation("PARSED", parsed.messageTypeId(),
                    parsed.instructions().size() + " instructions extracted");
            List<PaymentStatusResponse> responses = new ArrayList<>();

            for (InternalPaymentInstruction instruction : parsed.instructions()) {
                PaymentStatusResponse response = routeAndProcess(
                        instruction, parsed.messageTypeId(), request.getXmlMessage());
                responses.add(response);
            }

            if (idempotencyKey != null) {
                idempotencyService.storeResult(idempotencyKey,
                        "PROCESSED:" + responses.size() + " instructions");
                auditService.logIdempotencyCheck("RESULT_STORED", idempotencyKey, "Processed " + responses.size() + " instructions");
            }

            return responses;
        } catch (IdempotencyException e) {
            throw e;
        } catch (Exception e) {
            if (idempotencyKey != null) {
                idempotencyService.releaseLock(idempotencyKey);
            }
            throw e;
        }
    }

    private PaymentStatusResponse routeAndProcess(InternalPaymentInstruction instruction,
                                                    String messageTypeId, String rawXml) {
        OperationType opType = instruction.getOperationType();
        if (opType == null) opType = OperationType.CREDIT_TRANSFER;

        return switch (opType) {
            case DIRECT_DEBIT -> processDirectDebit(instruction, messageTypeId, rawXml);
            case REVERSAL -> processReversal(instruction, messageTypeId, rawXml);
            case RETURN -> processReturn(instruction, messageTypeId, rawXml);
            case CANCELLATION -> processCancellation(instruction, messageTypeId, rawXml);
            case LOAN_DISBURSEMENT -> processLoanDisbursement(instruction, messageTypeId, rawXml);
            case LOAN_REPAYMENT -> processLoanRepayment(instruction, messageTypeId, rawXml);
            case STATUS_REQUEST -> processStatusRequest(instruction, messageTypeId, rawXml);
            case ACCOUNT_REPORT_REQUEST -> processAccountReportRequest(instruction, messageTypeId, rawXml);
            case MANDATE_INITIATION -> processMandateInitiation(instruction, messageTypeId, rawXml);
            case MANDATE_AMENDMENT -> processMandateAmendment(instruction, messageTypeId, rawXml);
            case ACCOUNT_OPENING -> processAccountOpening(instruction, messageTypeId, rawXml);
            case ACCOUNT_MODIFICATION -> processAccountModification(instruction, messageTypeId, rawXml);
            case ACCOUNT_CLOSING -> processAccountClosing(instruction, messageTypeId, rawXml);
            default -> processCreditTransfer(instruction, messageTypeId, rawXml);
        };
    }

    private PaymentStatusResponse processCreditTransfer(InternalPaymentInstruction instruction,
                                                          String messageTypeId, String rawXml) {
        if ("LOAN".equalsIgnoreCase(instruction.getPurposeCode())) {
            if (instruction.getLoanId() != null) {
                return processLoanDisbursement(instruction, messageTypeId, rawXml);
            }
        }

        PaymentMessage paymentMessage = buildPaymentMessage(instruction, messageTypeId, rawXml);
        paymentMessage = paymentMessageRepository.save(paymentMessage);
        auditService.logAction(paymentMessage, "RECEIVED", "Inbound " + messageTypeId + " received");

        try {
            paymentMessage.setStatus(MessageStatus.VALIDATED);
            paymentMessageRepository.save(paymentMessage);
            auditService.logAction(paymentMessage, "VALIDATED", "Message validation passed");

            paymentMessage.setStatus(MessageStatus.PROCESSING);
            paymentMessageRepository.save(paymentMessage);
            auditService.logAction(paymentMessage, "PROCESSING", "Executing Fineract credit transfer");

            String fineractTxnId = executeFineractCreditTransfer(instruction);

            paymentMessage.setFineractTransactionId(fineractTxnId);
            paymentMessage.setStatus(MessageStatus.COMPLETED);
            paymentMessageRepository.save(paymentMessage);
            auditService.logAction(paymentMessage, "COMPLETED",
                    "Fineract transaction ID: " + fineractTxnId);

            String statusXml = pacs002Mapper.buildStatusReport(paymentMessage, null, null);
            publishToKafka(paymentMessage.getMessageId(), paymentMessage.getMessageType(), statusXml);
            return pacs002Mapper.toStatusResponse(paymentMessage, statusXml);

        } catch (Exception e) {
            log.error("Payment processing failed for {}: {}", paymentMessage.getMessageId(), e.getMessage());
            paymentMessage.setStatus(MessageStatus.FAILED);
            paymentMessage.setErrorMessage(e.getMessage());
            paymentMessageRepository.save(paymentMessage);
            auditService.logAction(paymentMessage, "FAILED", e.getMessage());

            String statusXml = pacs002Mapper.buildStatusReport(paymentMessage, "NARR", e.getMessage());
            publishToKafka(paymentMessage.getMessageId(), paymentMessage.getMessageType(), statusXml);
            return pacs002Mapper.toStatusResponse(paymentMessage, statusXml);
        }
    }

    private PaymentStatusResponse processDirectDebit(InternalPaymentInstruction instruction,
                                                       String messageTypeId, String rawXml) {
        try {
            auditService.logSystemEvent("PAYMENT", "ROUTING", "PaymentService", "payment", instruction.getMessageId(), "Routing DIRECT_DEBIT to DirectDebitService");
            String txnId = directDebitService.processDirectDebit(instruction, rawXml);
            PaymentMessage msg = paymentMessageRepository.findByFineractTransactionId(txnId).orElse(null);
            if (msg == null) msg = buildCompletedMessage(instruction, messageTypeId, rawXml, txnId);
            String statusXml = pacs002Mapper.buildStatusReport(msg, null, null);
            return pacs002Mapper.toStatusResponse(msg, statusXml);
        } catch (Exception e) {
            return buildFailedResponse(instruction, messageTypeId, rawXml, e);
        }
    }

    private PaymentStatusResponse processReversal(InternalPaymentInstruction instruction,
                                                     String messageTypeId, String rawXml) {
        try {
            auditService.logSystemEvent("PAYMENT", "ROUTING", "PaymentService", "payment", instruction.getMessageId(), "Routing REVERSAL for original: " + instruction.getOriginalMessageId());
            String txnId = reversalService.processReversal(instruction, rawXml);
            PaymentMessage msg = buildCompletedMessage(instruction, messageTypeId, rawXml, txnId);
            String statusXml = pacs002Mapper.buildStatusReport(msg, null, null);
            return pacs002Mapper.toStatusResponse(msg, statusXml);
        } catch (Exception e) {
            return buildFailedResponse(instruction, messageTypeId, rawXml, e);
        }
    }

    private PaymentStatusResponse processReturn(InternalPaymentInstruction instruction,
                                                   String messageTypeId, String rawXml) {
        try {
            auditService.logSystemEvent("PAYMENT", "ROUTING", "PaymentService", "payment", instruction.getMessageId(), "Routing RETURN for original E2E: " + instruction.getOriginalEndToEndId());
            String txnId = reversalService.processReturn(instruction, rawXml);
            PaymentMessage msg = buildCompletedMessage(instruction, messageTypeId, rawXml, txnId);
            String statusXml = pacs002Mapper.buildStatusReport(msg, null, null);
            return pacs002Mapper.toStatusResponse(msg, statusXml);
        } catch (Exception e) {
            return buildFailedResponse(instruction, messageTypeId, rawXml, e);
        }
    }

    private PaymentStatusResponse processCancellation(InternalPaymentInstruction instruction,
                                                        String messageTypeId, String rawXml) {
        try {
            auditService.logSystemEvent("PAYMENT", "ROUTING", "PaymentService", "payment", instruction.getMessageId(), "Routing CANCELLATION for original: " + instruction.getOriginalMessageId());
            String resolutionXml = reversalService.processCancellation(instruction, rawXml);
            PaymentMessage msg = PaymentMessage.builder()
                    .messageId(IdGenerator.generateMessageId())
                    .messageType("camt.029")
                    .direction(MessageDirection.OUTBOUND)
                    .status(MessageStatus.COMPLETED)
                    .rawXml(resolutionXml)
                    .build();
            msg = paymentMessageRepository.save(msg);
            return pacs002Mapper.toStatusResponse(msg, resolutionXml);
        } catch (Exception e) {
            return buildFailedResponse(instruction, messageTypeId, rawXml, e);
        }
    }

    private PaymentStatusResponse processLoanDisbursement(InternalPaymentInstruction instruction,
                                                            String messageTypeId, String rawXml) {
        try {
            auditService.logSystemEvent("PAYMENT", "ROUTING", "PaymentService", "loan", instruction.getLoanId(), "Routing LOAN_DISBURSEMENT, amount=" + instruction.getAmount() + " " + instruction.getCurrency());
            String txnId = fineractClient.executeLoanDisbursement(
                    instruction.getLoanId(), instruction.getAmount(),
                    instruction.getRequestedExecutionDate() != null
                            ? instruction.getRequestedExecutionDate() : LocalDate.now(),
                    instruction.getRemittanceInfo());
            PaymentMessage msg = buildCompletedMessage(instruction, messageTypeId, rawXml, txnId);
            msg.setOperationType("LOAN_DISBURSEMENT");
            paymentMessageRepository.save(msg);
            auditService.logAction(msg, "COMPLETED", "Loan disbursement executed: " + txnId);
            String statusXml = pacs002Mapper.buildStatusReport(msg, null, null);
            return pacs002Mapper.toStatusResponse(msg, statusXml);
        } catch (Exception e) {
            return buildFailedResponse(instruction, messageTypeId, rawXml, e);
        }
    }

    private PaymentStatusResponse processLoanRepayment(InternalPaymentInstruction instruction,
                                                         String messageTypeId, String rawXml) {
        try {
            auditService.logSystemEvent("PAYMENT", "ROUTING", "PaymentService", "loan", instruction.getLoanId(), "Routing LOAN_REPAYMENT, amount=" + instruction.getAmount() + " " + instruction.getCurrency());
            String txnId = fineractClient.executeLoanRepayment(
                    instruction.getLoanId(), instruction.getAmount(),
                    instruction.getRequestedExecutionDate() != null
                            ? instruction.getRequestedExecutionDate() : LocalDate.now(),
                    instruction.getRemittanceInfo());
            PaymentMessage msg = buildCompletedMessage(instruction, messageTypeId, rawXml, txnId);
            msg.setOperationType("LOAN_REPAYMENT");
            paymentMessageRepository.save(msg);
            auditService.logAction(msg, "COMPLETED", "Loan repayment executed: " + txnId);
            String statusXml = pacs002Mapper.buildStatusReport(msg, null, null);
            return pacs002Mapper.toStatusResponse(msg, statusXml);
        } catch (Exception e) {
            return buildFailedResponse(instruction, messageTypeId, rawXml, e);
        }
    }

    private PaymentStatusResponse processStatusRequest(InternalPaymentInstruction instruction,
                                                         String messageTypeId, String rawXml) {
        try {
            auditService.logSystemEvent("PAYMENT", "STATUS_INQUIRY", "PaymentService", "payment", instruction.getOriginalMessageId() != null ? instruction.getOriginalMessageId() : instruction.getOriginalEndToEndId(), "Payment status inquiry received");
            if (instruction.getOriginalMessageId() != null) {
                return getPaymentStatus(instruction.getOriginalMessageId());
            }
            if (instruction.getOriginalEndToEndId() != null) {
                return getPaymentStatusByEndToEndId(instruction.getOriginalEndToEndId());
            }
            throw new PaymentProcessingException("Status request has no original message reference");
        } catch (Exception e) {
            return buildFailedResponse(instruction, messageTypeId, rawXml, e);
        }
    }

    private PaymentStatusResponse processAccountReportRequest(InternalPaymentInstruction instruction,
                                                                String messageTypeId, String rawXml) {
        try {
            String accountId = instruction.getDebtorAccountOther() != null
                    ? instruction.getDebtorAccountOther()
                    : instruction.getDebtorAccountIban();

            String reportType = instruction.getReportType();
            auditService.logSystemEvent("PAYMENT", "REPORT_REQUEST", "PaymentService", "account", accountId, "Account report requested, type=" + reportType);
            String reportXml;

            if ("camt.052".equals(reportType) || reportType == null) {
                reportXml = statementService.generateIntradayReport(accountId);
            } else {
                reportXml = statementService.generateStatementForAccount(accountId);
            }

            PaymentMessage msg = PaymentMessage.builder()
                    .messageId(IdGenerator.generateMessageId())
                    .messageType(reportType != null ? reportType : "camt.052")
                    .direction(MessageDirection.OUTBOUND)
                    .status(MessageStatus.COMPLETED)
                    .rawXml(reportXml)
                    .debtorAccount(accountId)
                    .build();
            msg = paymentMessageRepository.save(msg);
            return pacs002Mapper.toStatusResponse(msg, reportXml);
        } catch (Exception e) {
            return buildFailedResponse(instruction, messageTypeId, rawXml, e);
        }
    }

    public PaymentStatusResponse getPaymentStatus(String messageId) {
        PaymentMessage pm = paymentMessageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new PaymentProcessingException("Payment not found: " + messageId));
        String statusXml = pacs002Mapper.buildStatusReport(pm, null, null);
        return pacs002Mapper.toStatusResponse(pm, statusXml);
    }

    public PaymentStatusResponse getPaymentStatusByEndToEndId(String endToEndId) {
        PaymentMessage pm = paymentMessageRepository.findByEndToEndId(endToEndId)
                .orElseThrow(() -> new PaymentProcessingException(
                        "Payment not found for E2E ID: " + endToEndId));
        String statusXml = pacs002Mapper.buildStatusReport(pm, null, null);
        return pacs002Mapper.toStatusResponse(pm, statusXml);
    }

    private String executeFineractCreditTransfer(InternalPaymentInstruction instruction) {
        String debtorAccount = resolveAccount(instruction);
        String creditorAccount = resolveCreditorAccount(instruction);

        if (debtorAccount != null && creditorAccount != null) {
            instruction.setDebtorAccountOther(debtorAccount);
            instruction.setCreditorAccountOther(creditorAccount);
            return fineractClient.executeTransfer(instruction);
        } else if (creditorAccount != null) {
            return fineractClient.executeDeposit(creditorAccount, instruction.getAmount(),
                    instruction.getCurrency(), instruction.getRemittanceInfo());
        } else if (debtorAccount != null) {
            return fineractClient.executeWithdrawal(debtorAccount, instruction.getAmount(),
                    instruction.getCurrency(), instruction.getRemittanceInfo());
        } else {
            throw new PaymentProcessingException("No valid account found in payment instruction");
        }
    }

    private String resolveAccount(InternalPaymentInstruction instruction) {
        try {
            return accountResolution.resolveToFineractId(
                    instruction.getDebtorAccountIban(),
                    instruction.getDebtorAgentBic(),
                    instruction.getDebtorAccountOther());
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveCreditorAccount(InternalPaymentInstruction instruction) {
        try {
            return accountResolution.resolveToFineractId(
                    instruction.getCreditorAccountIban(),
                    instruction.getCreditorAgentBic(),
                    instruction.getCreditorAccountOther());
        } catch (Exception e) {
            return null;
        }
    }

    private PaymentMessage buildPaymentMessage(InternalPaymentInstruction instruction,
                                                String messageTypeId, String rawXml) {
        return PaymentMessage.builder()
                .messageId(instruction.getMessageId() != null
                        ? instruction.getMessageId() : IdGenerator.generateMessageId())
                .messageType(messageTypeId)
                .direction(MessageDirection.INBOUND)
                .status(MessageStatus.RECEIVED)
                .operationType(instruction.getOperationType() != null
                        ? instruction.getOperationType().name() : "CREDIT_TRANSFER")
                .rawXml(rawXml)
                .endToEndId(instruction.getEndToEndId())
                .instructionId(instruction.getInstructionId())
                .debtorName(instruction.getDebtorName())
                .debtorAccount(instruction.getDebtorAccountIban() != null
                        ? instruction.getDebtorAccountIban() : instruction.getDebtorAccountOther())
                .creditorName(instruction.getCreditorName())
                .creditorAccount(instruction.getCreditorAccountIban() != null
                        ? instruction.getCreditorAccountIban() : instruction.getCreditorAccountOther())
                .amount(instruction.getAmount())
                .currency(instruction.getCurrency())
                .build();
    }

    private PaymentMessage buildCompletedMessage(InternalPaymentInstruction instruction,
                                                   String messageTypeId, String rawXml, String txnId) {
        PaymentMessage msg = buildPaymentMessage(instruction, messageTypeId, rawXml);
        msg.setStatus(MessageStatus.COMPLETED);
        msg.setFineractTransactionId(txnId);
        return paymentMessageRepository.save(msg);
    }

    private PaymentStatusResponse processMandateInitiation(InternalPaymentInstruction instruction,
                                                               String messageTypeId, String rawXml) {
        try {
            directDebitService.createMandate(instruction);
            PaymentMessage msg = buildCompletedMessage(instruction, messageTypeId, rawXml, "MANDATE-" + instruction.getMandateId());
            msg.setOperationType("MANDATE_INITIATION");
            paymentMessageRepository.save(msg);
            auditService.logAction(msg, "COMPLETED", "Mandate initiated: " + instruction.getMandateId());
            auditService.logMandateLifecycle("INITIATED", instruction.getMandateId(), "Via pain.009, Fineract standing instruction created");
            String statusXml = pacs002Mapper.buildStatusReport(msg, null, null);
            return pacs002Mapper.toStatusResponse(msg, statusXml);
        } catch (Exception e) {
            return buildFailedResponse(instruction, messageTypeId, rawXml, e);
        }
    }

    private PaymentStatusResponse processMandateAmendment(InternalPaymentInstruction instruction,
                                                             String messageTypeId, String rawXml) {
        try {
            directDebitService.amendMandate(instruction);
            PaymentMessage msg = buildCompletedMessage(instruction, messageTypeId, rawXml, "MANDATE-AMD-" + instruction.getMandateId());
            msg.setOperationType("MANDATE_AMENDMENT");
            paymentMessageRepository.save(msg);
            auditService.logAction(msg, "COMPLETED", "Mandate amended: " + instruction.getMandateId());
            auditService.logMandateLifecycle("AMENDED", instruction.getMandateId(), "Via pain.010");
            String statusXml = pacs002Mapper.buildStatusReport(msg, null, null);
            return pacs002Mapper.toStatusResponse(msg, statusXml);
        } catch (Exception e) {
            return buildFailedResponse(instruction, messageTypeId, rawXml, e);
        }
    }

    private PaymentStatusResponse processAccountOpening(InternalPaymentInstruction instruction,
                                                           String messageTypeId, String rawXml) {
        try {
            String ackXml = accountManagementService.processAccountOpening(instruction, rawXml);
            PaymentMessage msg = PaymentMessage.builder()
                    .messageId(IdGenerator.generateMessageId())
                    .messageType("acmt.010")
                    .direction(MessageDirection.OUTBOUND)
                    .status(MessageStatus.COMPLETED)
                    .operationType("ACCOUNT_OPENING")
                    .rawXml(ackXml)
                    .build();
            msg = paymentMessageRepository.save(msg);
            auditService.logAction(msg, "COMPLETED", "Account opening completed, acmt.010 acknowledgement sent");
            return pacs002Mapper.toStatusResponse(msg, ackXml);
        } catch (Exception e) {
            return buildFailedResponse(instruction, messageTypeId, rawXml, e);
        }
    }

    private PaymentStatusResponse processAccountModification(InternalPaymentInstruction instruction,
                                                                String messageTypeId, String rawXml) {
        try {
            String ackXml = accountManagementService.processAccountModification(instruction, rawXml);
            PaymentMessage msg = PaymentMessage.builder()
                    .messageId(IdGenerator.generateMessageId())
                    .messageType("acmt.010")
                    .direction(MessageDirection.OUTBOUND)
                    .status(MessageStatus.COMPLETED)
                    .operationType("ACCOUNT_MODIFICATION")
                    .rawXml(ackXml)
                    .build();
            msg = paymentMessageRepository.save(msg);
            auditService.logAction(msg, "COMPLETED", "Account modification completed, acmt.010 acknowledgement sent");
            return pacs002Mapper.toStatusResponse(msg, ackXml);
        } catch (Exception e) {
            return buildFailedResponse(instruction, messageTypeId, rawXml, e);
        }
    }

    private PaymentStatusResponse processAccountClosing(InternalPaymentInstruction instruction,
                                                           String messageTypeId, String rawXml) {
        try {
            String ackXml = accountManagementService.processAccountClosing(instruction, rawXml);
            PaymentMessage msg = PaymentMessage.builder()
                    .messageId(IdGenerator.generateMessageId())
                    .messageType("acmt.010")
                    .direction(MessageDirection.OUTBOUND)
                    .status(MessageStatus.COMPLETED)
                    .operationType("ACCOUNT_CLOSING")
                    .rawXml(ackXml)
                    .build();
            msg = paymentMessageRepository.save(msg);
            auditService.logAction(msg, "COMPLETED", "Account closing completed, acmt.010 acknowledgement sent");
            return pacs002Mapper.toStatusResponse(msg, ackXml);
        } catch (Exception e) {
            return buildFailedResponse(instruction, messageTypeId, rawXml, e);
        }
    }

    private PaymentStatusResponse buildFailedResponse(InternalPaymentInstruction instruction,
                                                        String messageTypeId, String rawXml, Exception e) {
        log.error("Processing failed for {}: {}", messageTypeId, e.getMessage());
        PaymentMessage msg;
        String msgId = instruction.getMessageId();
        var existing = msgId != null ? paymentMessageRepository.findByMessageId(msgId) : java.util.Optional.<PaymentMessage>empty();
        if (existing.isPresent()) {
            msg = existing.get();
            msg.setStatus(MessageStatus.FAILED);
            msg.setErrorMessage(e.getMessage());
            msg = paymentMessageRepository.save(msg);
        } else {
            msg = buildPaymentMessage(instruction, messageTypeId, rawXml);
            msg.setStatus(MessageStatus.FAILED);
            msg.setErrorMessage(e.getMessage());
            msg = paymentMessageRepository.save(msg);
        }
        auditService.logAction(msg, "FAILED", e.getMessage());
        String statusXml = pacs002Mapper.buildStatusReport(msg, "NARR", e.getMessage());
        publishToKafka(msg.getMessageId(), messageTypeId, statusXml);
        return pacs002Mapper.toStatusResponse(msg, statusXml);
    }

    private void publishToKafka(String messageId, String messageType, String statusXml) {
        try {
            kafkaProducer.publishStatusReport(messageId, statusXml);
            kafkaProducer.publishOutbound(messageId, "pacs.002", statusXml);
        } catch (Exception e) {
            log.warn("Failed to publish to Kafka for {}: {}", messageId, e.getMessage());
        }
    }
}

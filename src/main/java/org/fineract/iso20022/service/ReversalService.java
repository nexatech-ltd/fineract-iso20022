package org.fineract.iso20022.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.exception.PaymentProcessingException;
import org.fineract.iso20022.mapper.Camt029Mapper;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.fineract.iso20022.model.entity.PaymentInvestigation;
import org.fineract.iso20022.model.entity.PaymentMessage;
import org.fineract.iso20022.model.enums.*;
import org.fineract.iso20022.repository.PaymentInvestigationRepository;
import org.fineract.iso20022.repository.PaymentMessageRepository;
import org.fineract.iso20022.util.IdGenerator;
import org.springframework.stereotype.Service;
@Slf4j
@Service
@RequiredArgsConstructor
public class ReversalService {

    private final FineractClientService fineractClient;
    private final PaymentMessageRepository paymentMessageRepository;
    private final PaymentInvestigationRepository investigationRepository;
    private final AuditService auditService;
    private final Camt029Mapper camt029Mapper;

    public String processReversal(InternalPaymentInstruction instruction, String rawXml) {
        log.info("Processing reversal for original message: {}", instruction.getOriginalMessageId());
        auditService.logSystemEvent("REVERSAL", "RECEIVED", "ReversalService",
                "payment", instruction.getOriginalMessageId(), "Reversal request received");

        PaymentMessage originalPayment = findOriginalPayment(instruction);
        auditService.logSystemEvent("REVERSAL", originalPayment != null ? "ORIGINAL_FOUND" : "ORIGINAL_NOT_FOUND",
                "ReversalService", "payment", instruction.getOriginalMessageId(),
                originalPayment != null ? "Original txn: " + originalPayment.getFineractTransactionId() : "Not found in database");
        if (originalPayment == null) {
            throw new PaymentProcessingException("Original payment not found for reversal: "
                    + instruction.getOriginalMessageId());
        }

        String fineractTxnId = originalPayment.getFineractTransactionId();
        String accountId = originalPayment.getDebtorAccount() != null
                ? originalPayment.getDebtorAccount() : originalPayment.getCreditorAccount();

        if (fineractTxnId == null || accountId == null) {
            throw new PaymentProcessingException(
                    "Original payment has no Fineract transaction to reverse");
        }

        String undoTxnId = fineractClient.undoTransaction(accountId, fineractTxnId);
        auditService.logSystemEvent("REVERSAL", "UNDO_EXECUTED", "ReversalService",
                "transaction", undoTxnId, "Reversed txn " + fineractTxnId + " on account " + accountId);

        PaymentMessage msg = PaymentMessage.builder()
                .messageId(instruction.getMessageId() != null ? instruction.getMessageId() : IdGenerator.generateMessageId())
                .messageType(instruction.getOriginalMessageType())
                .direction(MessageDirection.INBOUND)
                .status(MessageStatus.COMPLETED)
                .operationType("REVERSAL")
                .rawXml(rawXml)
                .debtorAccount(originalPayment.getDebtorAccount())
                .creditorAccount(originalPayment.getCreditorAccount())
                .amount(instruction.getAmount() != null ? instruction.getAmount() : originalPayment.getAmount())
                .currency(instruction.getCurrency() != null ? instruction.getCurrency() : originalPayment.getCurrency())
                .fineractTransactionId(undoTxnId)
                .originalMessageRef(originalPayment.getMessageId())
                .build();
        msg = paymentMessageRepository.save(msg);
        auditService.logAction(msg, "COMPLETED", "Reversal executed: " + undoTxnId);

        return undoTxnId;
    }

    public String processReturn(InternalPaymentInstruction instruction, String rawXml) {
        log.info("Processing return for original E2E: {}", instruction.getOriginalEndToEndId());
        auditService.logSystemEvent("RETURN", "RECEIVED", "ReversalService",
                "payment", instruction.getOriginalEndToEndId(), "Return request received");

        PaymentMessage originalPayment = findOriginalPayment(instruction);
        if (originalPayment == null) {
            throw new PaymentProcessingException("Original payment not found for return");
        }

        String fineractTxnId = originalPayment.getFineractTransactionId();
        String accountId = originalPayment.getDebtorAccount() != null
                ? originalPayment.getDebtorAccount() : originalPayment.getCreditorAccount();

        if (fineractTxnId == null || accountId == null) {
            throw new PaymentProcessingException(
                    "Original payment has no Fineract transaction to return");
        }

        String undoTxnId = fineractClient.undoTransaction(accountId, fineractTxnId);
        auditService.logSystemEvent("RETURN", "UNDO_EXECUTED", "ReversalService",
                "transaction", undoTxnId, "Returned txn " + fineractTxnId + " on account " + accountId);

        PaymentMessage msg = PaymentMessage.builder()
                .messageId(instruction.getMessageId() != null ? instruction.getMessageId() : IdGenerator.generateMessageId())
                .messageType("pacs.004")
                .direction(MessageDirection.INBOUND)
                .status(MessageStatus.COMPLETED)
                .operationType("RETURN")
                .rawXml(rawXml)
                .debtorAccount(originalPayment.getDebtorAccount())
                .creditorAccount(originalPayment.getCreditorAccount())
                .amount(instruction.getAmount() != null ? instruction.getAmount() : originalPayment.getAmount())
                .currency(instruction.getCurrency() != null ? instruction.getCurrency() : originalPayment.getCurrency())
                .fineractTransactionId(undoTxnId)
                .originalMessageRef(originalPayment.getMessageId())
                .build();
        msg = paymentMessageRepository.save(msg);
        auditService.logAction(msg, "COMPLETED", "Return executed: " + undoTxnId);

        return undoTxnId;
    }

    public String processCancellation(InternalPaymentInstruction instruction, String rawXml) {
        log.info("Processing cancellation for original message: {}", instruction.getOriginalMessageId());
        auditService.logSystemEvent("CANCELLATION", "RECEIVED", "ReversalService",
                "payment", instruction.getOriginalMessageId(), "Cancellation request, reason=" + instruction.getCancellationReasonCode());

        PaymentMessage originalPayment = findOriginalPayment(instruction);
        PaymentInvestigation investigation = PaymentInvestigation.builder()
                .investigationId(instruction.getInstructionId() != null
                        ? instruction.getInstructionId() : IdGenerator.generateMessageId())
                .originalMessageId(instruction.getOriginalMessageId())
                .originalEndToEndId(instruction.getOriginalEndToEndId())
                .originalTransactionId(instruction.getOriginalTransactionId())
                .cancellationReasonCode(instruction.getCancellationReasonCode())
                .status(InvestigationStatus.PENDING)
                .originalPayment(originalPayment)
                .build();

        investigation = investigationRepository.save(investigation);
        auditService.logSystemEvent("CANCELLATION", "INVESTIGATION_CREATED", "ReversalService",
                "investigation", investigation.getInvestigationId(), "Status=" + investigation.getStatus());

        String undoTxnId = null;
        try {
            if (originalPayment != null && originalPayment.getFineractTransactionId() != null) {
                String accountId = originalPayment.getDebtorAccount() != null
                        ? originalPayment.getDebtorAccount() : originalPayment.getCreditorAccount();
                undoTxnId = fineractClient.undoTransaction(accountId,
                        originalPayment.getFineractTransactionId());
                investigation.setFineractUndoTransactionId(undoTxnId);
                investigation.setStatus(InvestigationStatus.ACCEPTED);
                auditService.logSystemEvent("CANCELLATION", "UNDO_EXECUTED", "ReversalService",
                        "investigation", investigation.getInvestigationId(), "Undo txn=" + undoTxnId);
            } else {
                investigation.setStatus(InvestigationStatus.REJECTED);
                investigation.setCancellationReasonDescription("Original payment not found or has no Fineract transaction");
            }
        } catch (Exception e) {
            investigation.setStatus(InvestigationStatus.REJECTED);
            investigation.setCancellationReasonDescription(e.getMessage());
        }

        String resolutionXml = camt029Mapper.buildResolutionOfInvestigation(investigation);
        investigation.setResolutionXml(resolutionXml);
        investigationRepository.save(investigation);

        PaymentMessage msg = PaymentMessage.builder()
                .messageId(instruction.getMessageId() != null ? instruction.getMessageId() : IdGenerator.generateMessageId())
                .messageType("camt.056")
                .direction(MessageDirection.INBOUND)
                .status(investigation.getStatus() == InvestigationStatus.ACCEPTED
                        ? MessageStatus.COMPLETED : MessageStatus.REJECTED)
                .operationType("CANCELLATION")
                .rawXml(rawXml)
                .fineractTransactionId(undoTxnId)
                .originalMessageRef(instruction.getOriginalMessageId())
                .build();
        msg = paymentMessageRepository.save(msg);
        auditService.logAction(msg, investigation.getStatus().name(),
                "Cancellation investigation: " + investigation.getInvestigationId());

        return resolutionXml;
    }

    private PaymentMessage findOriginalPayment(InternalPaymentInstruction instruction) {
        if (instruction.getOriginalMessageId() != null) {
            return paymentMessageRepository.findByMessageId(instruction.getOriginalMessageId()).orElse(null);
        }
        if (instruction.getOriginalEndToEndId() != null) {
            return paymentMessageRepository.findByEndToEndId(instruction.getOriginalEndToEndId()).orElse(null);
        }
        return null;
    }
}

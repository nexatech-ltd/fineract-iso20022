package org.fineract.iso20022.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.exception.PaymentProcessingException;
import org.fineract.iso20022.mapper.Pain014Mapper;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.fineract.iso20022.model.entity.DirectDebitMandate;
import org.fineract.iso20022.model.entity.PaymentMessage;
import org.fineract.iso20022.model.enums.MandateStatus;
import org.fineract.iso20022.model.enums.MessageDirection;
import org.fineract.iso20022.model.enums.MessageStatus;
import org.fineract.iso20022.repository.DirectDebitMandateRepository;
import org.fineract.iso20022.repository.PaymentMessageRepository;
import org.fineract.iso20022.util.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectDebitService {

    private final FineractClientService fineractClient;
    private final AccountResolutionService accountResolution;
    private final DirectDebitMandateRepository mandateRepository;
    private final PaymentMessageRepository paymentMessageRepository;
    private final AuditService auditService;
    private final Pain014Mapper pain014Mapper;

    public String processDirectDebit(InternalPaymentInstruction instruction, String rawXml) {
        log.info("Processing direct debit: mandateId={}, amount={} {}",
                instruction.getMandateId(), instruction.getAmount(), instruction.getCurrency());
        auditService.logSystemEvent("DIRECT_DEBIT", "RECEIVED", "DirectDebitService",
                "payment", instruction.getMessageId(), 
                "mandateId=" + instruction.getMandateId() + ", amount=" + instruction.getAmount() + " " + instruction.getCurrency());

        if (instruction.getMandateId() != null) {
            DirectDebitMandate mandate = mandateRepository.findByMandateId(instruction.getMandateId())
                    .orElse(null);

            if (mandate == null) {
                mandate = createMandate(instruction);
                log.info("Created new mandate: {}", mandate.getMandateId());
            }

            validateMandate(mandate, instruction);
            auditService.logMandateLifecycle("VALIDATED", mandate.getMandateId(),
                    "Mandate validated for direct debit, maxAmount=" + mandate.getMaxAmount());
        }

        String debtorAccountId = accountResolution.resolveToFineractId(
                instruction.getDebtorAccountIban(),
                instruction.getDebtorAgentBic(),
                instruction.getDebtorAccountOther());

        String creditorAccountId = accountResolution.resolveToFineractId(
                instruction.getCreditorAccountIban(),
                instruction.getCreditorAgentBic(),
                instruction.getCreditorAccountOther());

        String txnId = fineractClient.executeWithdrawal(
                debtorAccountId, instruction.getAmount(),
                instruction.getCurrency(),
                "Direct Debit - Mandate: " + instruction.getMandateId());
        auditService.logSystemEvent("DIRECT_DEBIT", "WITHDRAWAL_EXECUTED", "DirectDebitService",
                "transaction", txnId, "Debtor account=" + debtorAccountId + ", amount=" + instruction.getAmount());

        fineractClient.executeDeposit(
                creditorAccountId, instruction.getAmount(),
                instruction.getCurrency(),
                "Direct Debit Collection - Mandate: " + instruction.getMandateId());
        auditService.logSystemEvent("DIRECT_DEBIT", "DEPOSIT_EXECUTED", "DirectDebitService",
                "transaction", txnId, "Creditor account=" + creditorAccountId);

        PaymentMessage msg = PaymentMessage.builder()
                .messageId(instruction.getMessageId() != null ? instruction.getMessageId() : IdGenerator.generateMessageId())
                .messageType(instruction.getOriginalMessageType())
                .direction(MessageDirection.INBOUND)
                .status(MessageStatus.COMPLETED)
                .operationType("DIRECT_DEBIT")
                .rawXml(rawXml)
                .debtorAccount(debtorAccountId)
                .creditorAccount(creditorAccountId)
                .amount(instruction.getAmount())
                .currency(instruction.getCurrency())
                .fineractTransactionId(txnId)
                .build();
        msg = paymentMessageRepository.save(msg);
        auditService.logAction(msg, "COMPLETED", "Direct debit executed: " + txnId);

        return txnId;
    }

    public String buildActivationStatus(String originalMsgId, boolean accepted, String reason) {
        String status = accepted ? "ACTC" : "RJCT";
        return pain014Mapper.buildActivationStatusReport(originalMsgId, "pain.008", status, null, reason);
    }

    public DirectDebitMandate createMandate(InternalPaymentInstruction instruction) {
        String fineractInstructionId = null;
        try {
            String debtorAccountId = accountResolution.resolveToFineractId(
                    instruction.getDebtorAccountIban(),
                    instruction.getDebtorAgentBic(),
                    instruction.getDebtorAccountOther());

            String creditorAccountId = accountResolution.resolveToFineractId(
                    instruction.getCreditorAccountIban(),
                    instruction.getCreditorAgentBic(),
                    instruction.getCreditorAccountOther());

            fineractInstructionId = fineractClient.createStandingInstruction(
                    debtorAccountId, creditorAccountId,
                    instruction.getAmount(), null,
                    "DD Mandate: " + instruction.getMandateId());
            auditService.logMandateLifecycle("STANDING_INSTRUCTION_CREATED", instruction.getMandateId(),
                    "Fineract standing instruction: " + fineractInstructionId);
        } catch (Exception e) {
            log.warn("Could not create Fineract standing instruction for mandate {}: {}",
                    instruction.getMandateId(), e.getMessage());
        }

        DirectDebitMandate mandate = DirectDebitMandate.builder()
                .mandateId(instruction.getMandateId() != null
                        ? instruction.getMandateId() : IdGenerator.generateMessageId())
                .creditorName(instruction.getCreditorName())
                .creditorAccount(instruction.getCreditorAccountIban() != null
                        ? instruction.getCreditorAccountIban() : instruction.getCreditorAccountOther())
                .creditorAgentBic(instruction.getCreditorAgentBic())
                .debtorName(instruction.getDebtorName())
                .debtorAccount(instruction.getDebtorAccountIban() != null
                        ? instruction.getDebtorAccountIban() : instruction.getDebtorAccountOther())
                .debtorAgentBic(instruction.getDebtorAgentBic())
                .maxAmount(instruction.getAmount())
                .currency(instruction.getCurrency())
                .status(MandateStatus.ACTIVE)
                .fineractStandingInstructionId(fineractInstructionId)
                .build();

        DirectDebitMandate saved = mandateRepository.save(mandate);
        auditService.logMandateLifecycle("CREATED", saved.getMandateId(),
                "creditor=" + saved.getCreditorName() + ", debtor=" + saved.getDebtorName() + ", maxAmount=" + saved.getMaxAmount() + " " + saved.getCurrency());
        return saved;
    }

    public DirectDebitMandate amendMandate(InternalPaymentInstruction instruction) {
        String mandateId = instruction.getMandateId();
        if (mandateId == null) {
            throw new PaymentProcessingException("Mandate ID is required for amendment");
        }

        DirectDebitMandate mandate = mandateRepository.findByMandateId(mandateId)
                .orElseThrow(() -> new PaymentProcessingException("Mandate not found: " + mandateId));

        if (instruction.getAmount() != null) mandate.setMaxAmount(instruction.getAmount());
        if (instruction.getCurrency() != null) mandate.setCurrency(instruction.getCurrency());
        if (instruction.getCreditorName() != null) mandate.setCreditorName(instruction.getCreditorName());
        if (instruction.getDebtorName() != null) mandate.setDebtorName(instruction.getDebtorName());
        if (instruction.getCreditorAccountIban() != null || instruction.getCreditorAccountOther() != null) {
            mandate.setCreditorAccount(instruction.getCreditorAccountIban() != null
                    ? instruction.getCreditorAccountIban() : instruction.getCreditorAccountOther());
        }
        if (instruction.getDebtorAccountIban() != null || instruction.getDebtorAccountOther() != null) {
            mandate.setDebtorAccount(instruction.getDebtorAccountIban() != null
                    ? instruction.getDebtorAccountIban() : instruction.getDebtorAccountOther());
        }

        mandate = mandateRepository.save(mandate);
        auditService.logMandateLifecycle("AMENDED", mandateId,
                "Updated fields: amount=" + mandate.getMaxAmount() + " " + mandate.getCurrency());
        log.info("Amended mandate: {}", mandateId);
        return mandate;
    }

    public List<DirectDebitMandate> listMandates(MandateStatus status) {
        return status != null ? mandateRepository.findByStatus(status)
                : mandateRepository.findAll();
    }

    public DirectDebitMandate getMandate(String mandateId) {
        return mandateRepository.findByMandateId(mandateId)
                .orElseThrow(() -> new PaymentProcessingException("Mandate not found: " + mandateId));
    }

    @Transactional
    public void revokeMandate(String mandateId) {
        DirectDebitMandate mandate = getMandate(mandateId);
        mandate.setStatus(MandateStatus.CANCELLED);
        mandateRepository.save(mandate);
        auditService.logMandateLifecycle("REVOKED", mandateId, "Mandate cancelled");

        if (mandate.getFineractStandingInstructionId() != null) {
            try {
                fineractClient.deleteStandingInstruction(mandate.getFineractStandingInstructionId());
                auditService.logMandateLifecycle("STANDING_INSTRUCTION_DELETED", mandateId,
                        "Fineract instruction: " + mandate.getFineractStandingInstructionId());
            } catch (Exception e) {
                log.warn("Failed to delete Fineract standing instruction for mandate {}: {}",
                        mandateId, e.getMessage());
            }
        }

        log.info("Revoked mandate: {}", mandateId);
    }

    private void validateMandate(DirectDebitMandate mandate, InternalPaymentInstruction instruction) {
        if (mandate.getStatus() != MandateStatus.ACTIVE) {
            throw new PaymentProcessingException("Mandate " + mandate.getMandateId()
                    + " is not active: " + mandate.getStatus());
        }

        if (mandate.getMaxAmount() != null && instruction.getAmount() != null
                && instruction.getAmount().compareTo(mandate.getMaxAmount()) > 0) {
            throw new PaymentProcessingException("Amount " + instruction.getAmount()
                    + " exceeds mandate maximum: " + mandate.getMaxAmount());
        }
    }
}

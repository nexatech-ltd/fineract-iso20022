package org.fineract.iso20022.service;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.exception.PaymentProcessingException;
import org.fineract.iso20022.mapper.Acmt010Mapper;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.fineract.iso20022.model.entity.AccountMapping;
import org.fineract.iso20022.model.entity.PaymentMessage;
import org.fineract.iso20022.model.enums.MessageDirection;
import org.fineract.iso20022.model.enums.MessageStatus;
import org.fineract.iso20022.repository.AccountMappingRepository;
import org.fineract.iso20022.repository.PaymentMessageRepository;
import org.fineract.iso20022.util.IdGenerator;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
public class AccountManagementService {

    private final FineractClientService fineractClient;
    private final AccountMappingRepository accountMappingRepository;
    private final PaymentMessageRepository paymentMessageRepository;
    private final AuditService auditService;
    private final Acmt010Mapper acmt010Mapper;

    public String processAccountOpening(InternalPaymentInstruction instruction, String rawXml) {
        log.info("Processing account opening: msgId={}", instruction.getMessageId());

        PaymentMessage pm = PaymentMessage.builder()
                .messageId(instruction.getMessageId() != null ? instruction.getMessageId() : IdGenerator.generateMessageId())
                .messageType("acmt.007")
                .direction(MessageDirection.INBOUND)
                .status(MessageStatus.PROCESSING)
                .operationType("ACCOUNT_OPENING")
                .rawXml(rawXml)
                .debtorName(instruction.getDebtorName())
                .debtorAccount(instruction.getDebtorAccountIban() != null ? instruction.getDebtorAccountIban() : instruction.getDebtorAccountOther())
                .currency(instruction.getCurrency())
                .build();
        pm = paymentMessageRepository.save(pm);
        auditService.logAction(pm, "RECEIVED", "Account opening request received");

        try {
            String fineractAccountId = createSavingsAccount(instruction);

            saveAccountMapping(instruction, fineractAccountId);

            pm.setFineractTransactionId(fineractAccountId);
            pm.setStatus(MessageStatus.COMPLETED);
            paymentMessageRepository.save(pm);
            auditService.logAction(pm, "COMPLETED", "Fineract account created: " + fineractAccountId);

            return acmt010Mapper.buildAcknowledgement(
                    instruction.getMessageId(), fineractAccountId,
                    instruction.getRemittanceInfo(), instruction.getCurrency());
        } catch (Exception e) {
            pm.setStatus(MessageStatus.FAILED);
            pm.setErrorMessage(e.getMessage());
            paymentMessageRepository.save(pm);
            auditService.logAction(pm, "FAILED", e.getMessage());
            throw new PaymentProcessingException("Account opening failed: " + e.getMessage());
        }
    }

    public String processAccountModification(InternalPaymentInstruction instruction, String rawXml) {
        log.info("Processing account modification: msgId={}", instruction.getMessageId());

        PaymentMessage pm = PaymentMessage.builder()
                .messageId(instruction.getMessageId() != null ? instruction.getMessageId() : IdGenerator.generateMessageId())
                .messageType("acmt.008")
                .direction(MessageDirection.INBOUND)
                .status(MessageStatus.PROCESSING)
                .operationType("ACCOUNT_MODIFICATION")
                .rawXml(rawXml)
                .debtorAccount(instruction.getDebtorAccountIban() != null ? instruction.getDebtorAccountIban() : instruction.getDebtorAccountOther())
                .currency(instruction.getCurrency())
                .build();
        pm = paymentMessageRepository.save(pm);
        auditService.logAction(pm, "RECEIVED", "Account modification request received");

        try {
            String fineractAccountId = resolveAccountId(instruction);
            updateSavingsAccount(fineractAccountId, instruction);

            pm.setFineractTransactionId(fineractAccountId);
            pm.setStatus(MessageStatus.COMPLETED);
            paymentMessageRepository.save(pm);
            auditService.logAction(pm, "COMPLETED", "Fineract account updated: " + fineractAccountId);

            return acmt010Mapper.buildAcknowledgement(
                    instruction.getMessageId(), fineractAccountId,
                    instruction.getRemittanceInfo(), instruction.getCurrency());
        } catch (Exception e) {
            pm.setStatus(MessageStatus.FAILED);
            pm.setErrorMessage(e.getMessage());
            paymentMessageRepository.save(pm);
            auditService.logAction(pm, "FAILED", e.getMessage());
            throw new PaymentProcessingException("Account modification failed: " + e.getMessage());
        }
    }

    public String processAccountClosing(InternalPaymentInstruction instruction, String rawXml) {
        log.info("Processing account closing: msgId={}", instruction.getMessageId());

        PaymentMessage pm = PaymentMessage.builder()
                .messageId(instruction.getMessageId() != null ? instruction.getMessageId() : IdGenerator.generateMessageId())
                .messageType("acmt.019")
                .direction(MessageDirection.INBOUND)
                .status(MessageStatus.PROCESSING)
                .operationType("ACCOUNT_CLOSING")
                .rawXml(rawXml)
                .debtorAccount(instruction.getDebtorAccountIban() != null ? instruction.getDebtorAccountIban() : instruction.getDebtorAccountOther())
                .currency(instruction.getCurrency())
                .build();
        pm = paymentMessageRepository.save(pm);
        auditService.logAction(pm, "RECEIVED", "Account closing request received");

        try {
            String fineractAccountId = resolveAccountId(instruction);

            if (instruction.getCreditorAccountIban() != null || instruction.getCreditorAccountOther() != null) {
                log.info("Balance transfer account specified for closing");
            }

            closeSavingsAccount(fineractAccountId);

            deactivateAccountMapping(fineractAccountId);

            pm.setFineractTransactionId(fineractAccountId);
            pm.setStatus(MessageStatus.COMPLETED);
            paymentMessageRepository.save(pm);
            auditService.logAction(pm, "COMPLETED", "Fineract account closed: " + fineractAccountId);

            return acmt010Mapper.buildAcknowledgement(
                    instruction.getMessageId(), fineractAccountId,
                    null, instruction.getCurrency());
        } catch (Exception e) {
            pm.setStatus(MessageStatus.FAILED);
            pm.setErrorMessage(e.getMessage());
            paymentMessageRepository.save(pm);
            auditService.logAction(pm, "FAILED", e.getMessage());
            throw new PaymentProcessingException("Account closing failed: " + e.getMessage());
        }
    }

    private String createSavingsAccount(InternalPaymentInstruction instruction) {
        String externalId = instruction.getDebtorAccountIban() != null
                ? instruction.getDebtorAccountIban()
                : instruction.getDebtorAccountOther() != null
                        ? instruction.getDebtorAccountOther()
                        : instruction.getRemittanceInfo();
        return fineractClient.createSavingsAccount(
                instruction.getDebtorName(),
                instruction.getCurrency(),
                externalId);
    }

    private void updateSavingsAccount(String accountId, InternalPaymentInstruction instruction) {
        fineractClient.updateSavingsAccount(accountId, instruction.getRemittanceInfo());
    }

    private void closeSavingsAccount(String accountId) {
        fineractClient.closeSavingsAccount(accountId);
    }

    private String resolveAccountId(InternalPaymentInstruction instruction) {
        String iban = instruction.getDebtorAccountIban();
        String otherId = instruction.getDebtorAccountOther();

        if (otherId != null) {
            return otherId;
        }

        if (iban != null) {
            return accountMappingRepository.findByIbanAndActiveTrue(iban)
                    .map(AccountMapping::getFineractAccountId)
                    .orElseThrow(() -> new PaymentProcessingException("No Fineract account found for IBAN: " + iban));
        }

        throw new PaymentProcessingException("No account identifier provided");
    }

    private void saveAccountMapping(InternalPaymentInstruction instruction, String fineractAccountId) {
        AccountMapping mapping = AccountMapping.builder()
                .iban(instruction.getDebtorAccountIban())
                .externalId(instruction.getDebtorAccountOther())
                .fineractAccountId(fineractAccountId)
                .accountType("SAVINGS")
                .accountHolderName(instruction.getDebtorName())
                .currency(instruction.getCurrency())
                .active(true)
                .build();
        accountMappingRepository.save(mapping);
    }

    private void deactivateAccountMapping(String fineractAccountId) {
        accountMappingRepository.findByFineractAccountIdAndActiveTrue(fineractAccountId)
                .ifPresent(mapping -> {
                    mapping.setActive(false);
                    accountMappingRepository.save(mapping);
                });
    }
}

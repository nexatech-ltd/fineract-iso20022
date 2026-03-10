package org.fineract.iso20022.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fineract.iso20022.model.enums.OperationType;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalPaymentInstruction {

    private String messageId;
    private String instructionId;
    private String endToEndId;
    private String transactionId;

    private String debtorName;
    private String debtorAccountIban;
    private String debtorAccountOther;
    private String debtorAgentBic;

    private String creditorName;
    private String creditorAccountIban;
    private String creditorAccountOther;
    private String creditorAgentBic;

    private BigDecimal amount;
    private String currency;

    private LocalDate requestedExecutionDate;
    private String remittanceInfo;
    private String purposeCode;

    private String originalMessageType;

    @Builder.Default
    private OperationType operationType = OperationType.CREDIT_TRANSFER;

    // Charge bearer: SHA, BEN, OUR, DEBT, CRED, SHAR
    private String chargeBearerCode;

    // Intermediary agent
    private String intermediaryAgentBic;
    private String intermediaryAgentAccount;

    // Ultimate parties
    private String ultimateDebtorName;
    private String ultimateCreditorName;

    // FX information
    private String exchangeRateInfo;
    private String instructedCurrency;
    private BigDecimal instructedAmount;

    // Structured remittance
    private String structuredRemittanceRef;

    // Regulatory reporting (pass-through)
    private String regulatoryReporting;

    // Direct debit mandate
    private String mandateId;
    private LocalDate collectionDate;

    // Reversal / return / cancellation references
    private String originalTransactionId;
    private String originalMessageId;
    private String originalEndToEndId;
    private String returnReasonCode;
    private String cancellationReasonCode;
    private String reversalReasonCode;

    // Loan operations
    private String loanId;

    // Account reporting request
    private String reportType;
    private LocalDate reportFromDate;
    private LocalDate reportToDate;
}

package org.fineract.iso20022.model.enums;

import lombok.Getter;

@Getter
public enum Iso20022MessageType {

    PAIN_001("pain.001", "CustomerCreditTransferInitiation",
            "Customer Credit Transfer Initiation"),
    PAIN_002("pain.002", "CustomerPaymentStatusReport",
            "Customer Payment Status Report"),
    PAIN_007("pain.007", "CustomerPaymentReversal",
            "Customer Payment Reversal"),
    PAIN_008("pain.008", "CustomerDirectDebitInitiation",
            "Customer Direct Debit Initiation"),
    PAIN_014("pain.014", "CreditorPaymentActivationRequestStatusReport",
            "Creditor Payment Activation Request Status Report"),
    PACS_002("pacs.002", "FIToFIPaymentStatusReport",
            "FI to FI Payment Status Report"),
    PACS_003("pacs.003", "FIToFICustomerDirectDebit",
            "FI to FI Customer Direct Debit"),
    PACS_004("pacs.004", "PaymentReturn",
            "Payment Return"),
    PACS_008("pacs.008", "FIToFICustomerCreditTransfer",
            "FI to FI Customer Credit Transfer"),
    PACS_009("pacs.009", "FinancialInstitutionCreditTransfer",
            "Financial Institution Credit Transfer"),
    PACS_028("pacs.028", "FIToFIPaymentStatusRequest",
            "FI to FI Payment Status Request"),
    CAMT_029("camt.029", "ResolutionOfInvestigation",
            "Resolution of Investigation"),
    CAMT_052("camt.052", "BankToCustomerAccountReport",
            "Bank to Customer Account Report (Intraday)"),
    CAMT_053("camt.053", "BankToCustomerStatement",
            "Bank to Customer Statement"),
    CAMT_054("camt.054", "BankToCustomerDebitCreditNotification",
            "Bank to Customer Debit/Credit Notification"),
    CAMT_056("camt.056", "FIToFIPaymentCancellationRequest",
            "FI to FI Payment Cancellation Request"),
    CAMT_060("camt.060", "AccountReportingRequest",
            "Account Reporting Request"),
    PAIN_009("pain.009", "MandateInitiationRequest",
            "Mandate Initiation Request"),
    PAIN_010("pain.010", "MandateAmendmentRequest",
            "Mandate Amendment Request"),
    PAIN_012("pain.012", "MandateAcceptanceReport",
            "Mandate Acceptance Report"),
    ACMT_007("acmt.007", "AccountOpeningRequest",
            "Account Opening Request"),
    ACMT_008("acmt.008", "AccountOpeningAmendmentRequest",
            "Account Opening Amendment Request"),
    ACMT_010("acmt.010", "AccountRequestAcknowledgement",
            "Account Request Acknowledgement"),
    ACMT_019("acmt.019", "AccountClosingRequest",
            "Account Closing Request");

    private final String code;
    private final String businessName;
    private final String description;

    Iso20022MessageType(String code, String businessName, String description) {
        this.code = code;
        this.businessName = businessName;
        this.description = description;
    }

    public static Iso20022MessageType fromCode(String code) {
        for (Iso20022MessageType type : values()) {
            if (code != null && code.startsWith(type.code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ISO 20022 message type: " + code);
    }
}

package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxPain00800111;
import com.prowidesoftware.swift.model.mx.dic.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.exception.MessageParsingException;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.fineract.iso20022.model.enums.OperationType;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Pain008Mapper {

    public List<InternalPaymentInstruction> toPaymentInstructions(AbstractMX mx) {
        if (!(mx instanceof MxPain00800111 pain008)) {
            throw new MessageParsingException("Expected pain.008.001.11 but got: " + mx.getMxId().id());
        }

        CustomerDirectDebitInitiationV11 initiation = pain008.getCstmrDrctDbtInitn();
        if (initiation == null) {
            throw new MessageParsingException("pain.008 has no CstmrDrctDbtInitn element");
        }

        String msgId = initiation.getGrpHdr() != null ? initiation.getGrpHdr().getMsgId() : null;
        List<InternalPaymentInstruction> instructions = new ArrayList<>();

        List<PaymentInstruction45> pmtInfs = initiation.getPmtInf();
        if (pmtInfs == null || pmtInfs.isEmpty()) {
            throw new MessageParsingException("pain.008 has no payment instructions");
        }

        for (PaymentInstruction45 pmtInf : pmtInfs) {
            String creditorName = extractPartyName272(pmtInf.getCdtr());
            String creditorAccountIban = extractIban(pmtInf.getCdtrAcct());
            String creditorAccountOther = extractAccountId(pmtInf.getCdtrAcct());
            String creditorAgentBic = extractAgentBic8(pmtInf.getCdtrAgt());

            List<DirectDebitTransactionInformation32> txns = pmtInf.getDrctDbtTxInf();
            if (txns == null) continue;

            for (DirectDebitTransactionInformation32 txn : txns) {
                String mandateId = null;
                if (txn.getDrctDbtTx() != null && txn.getDrctDbtTx().getMndtRltdInf() != null) {
                    mandateId = txn.getDrctDbtTx().getMndtRltdInf().getMndtId();
                }

                InternalPaymentInstruction instruction = InternalPaymentInstruction.builder()
                        .messageId(msgId)
                        .instructionId(txn.getPmtId() != null ? txn.getPmtId().getInstrId() : null)
                        .endToEndId(txn.getPmtId() != null ? txn.getPmtId().getEndToEndId() : null)
                        .creditorName(creditorName)
                        .creditorAccountIban(creditorAccountIban)
                        .creditorAccountOther(creditorAccountOther)
                        .creditorAgentBic(creditorAgentBic)
                        .debtorName(extractPartyName272(txn.getDbtr()))
                        .debtorAccountIban(extractIban(txn.getDbtrAcct()))
                        .debtorAccountOther(extractAccountId(txn.getDbtrAcct()))
                        .debtorAgentBic(extractAgentBic8(txn.getDbtrAgt()))
                        .amount(extractAmount(txn.getInstdAmt()))
                        .currency(extractCurrency(txn.getInstdAmt()))
                        .collectionDate(pmtInf.getReqdColltnDt())
                        .mandateId(mandateId)
                        .operationType(OperationType.DIRECT_DEBIT)
                        .originalMessageType("pain.008")
                        .build();

                instructions.add(instruction);
            }
        }

        log.info("Parsed pain.008 message {} with {} direct debit instructions", msgId, instructions.size());
        return instructions;
    }

    private String extractPartyName272(PartyIdentification272 party) {
        return (party != null) ? party.getNm() : null;
    }

    private String extractAccountId(CashAccount40 account) {
        if (account == null || account.getId() == null) return null;
        if (account.getId().getIBAN() != null) return account.getId().getIBAN();
        if (account.getId().getOthr() != null) return account.getId().getOthr().getId();
        return null;
    }

    private String extractIban(CashAccount40 account) {
        if (account == null || account.getId() == null) return null;
        return account.getId().getIBAN();
    }

    private String extractAgentBic8(BranchAndFinancialInstitutionIdentification8 agent) {
        if (agent == null || agent.getFinInstnId() == null) return null;
        return agent.getFinInstnId().getBICFI();
    }

    private BigDecimal extractAmount(ActiveOrHistoricCurrencyAndAmount amt) {
        return (amt != null) ? amt.getValue() : null;
    }

    private String extractCurrency(ActiveOrHistoricCurrencyAndAmount amt) {
        return (amt != null) ? amt.getCcy() : null;
    }
}

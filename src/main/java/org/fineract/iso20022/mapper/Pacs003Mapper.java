package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxPacs00300111;
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
public class Pacs003Mapper {

    public List<InternalPaymentInstruction> toPaymentInstructions(AbstractMX mx) {
        if (!(mx instanceof MxPacs00300111 pacs003)) {
            throw new MessageParsingException("Expected pacs.003.001.11 but got: " + mx.getMxId().id());
        }

        FIToFICustomerDirectDebitV11 dd = pacs003.getFIToFICstmrDrctDbt();
        if (dd == null) {
            throw new MessageParsingException("pacs.003 has no FIToFICstmrDrctDbt element");
        }

        String msgId = dd.getGrpHdr() != null ? dd.getGrpHdr().getMsgId() : null;
        List<InternalPaymentInstruction> instructions = new ArrayList<>();

        List<DirectDebitTransactionInformation31> txns = dd.getDrctDbtTxInf();
        if (txns == null || txns.isEmpty()) {
            throw new MessageParsingException("pacs.003 has no direct debit transactions");
        }

        for (DirectDebitTransactionInformation31 txn : txns) {
            String mandateId = null;
            if (txn.getDrctDbtTx() != null && txn.getDrctDbtTx().getMndtRltdInf() != null) {
                mandateId = txn.getDrctDbtTx().getMndtRltdInf().getMndtId();
            }

            InternalPaymentInstruction instruction = InternalPaymentInstruction.builder()
                    .messageId(msgId)
                    .instructionId(txn.getPmtId() != null ? txn.getPmtId().getInstrId() : null)
                    .endToEndId(txn.getPmtId() != null ? txn.getPmtId().getEndToEndId() : null)
                    .transactionId(txn.getPmtId() != null ? txn.getPmtId().getTxId() : null)
                    .creditorName(extractPartyName272(txn.getCdtr()))
                    .creditorAccountIban(extractIban(txn.getCdtrAcct()))
                    .creditorAccountOther(extractAccountId(txn.getCdtrAcct()))
                    .creditorAgentBic(extractAgentBic8(txn.getCdtrAgt()))
                    .debtorName(extractPartyName272(txn.getDbtr()))
                    .debtorAccountIban(extractIban(txn.getDbtrAcct()))
                    .debtorAccountOther(extractAccountId(txn.getDbtrAcct()))
                    .debtorAgentBic(extractAgentBic8(txn.getDbtrAgt()))
                    .amount(txn.getIntrBkSttlmAmt() != null ? txn.getIntrBkSttlmAmt().getValue() : null)
                    .currency(txn.getIntrBkSttlmAmt() != null ? txn.getIntrBkSttlmAmt().getCcy() : null)
                    .collectionDate(txn.getReqdColltnDt())
                    .mandateId(mandateId)
                    .operationType(OperationType.DIRECT_DEBIT)
                    .originalMessageType("pacs.003")
                    .build();

            instructions.add(instruction);
        }

        log.info("Parsed pacs.003 message {} with {} direct debit transactions", msgId, instructions.size());
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
}

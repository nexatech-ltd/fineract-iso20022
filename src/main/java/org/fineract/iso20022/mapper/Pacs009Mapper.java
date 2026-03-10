package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxPacs00900112;
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
public class Pacs009Mapper {

    public List<InternalPaymentInstruction> toPaymentInstructions(AbstractMX mx) {
        if (!(mx instanceof MxPacs00900112 pacs009)) {
            throw new MessageParsingException("Expected pacs.009.001.12 but got: " + mx.getMxId().id());
        }

        FinancialInstitutionCreditTransferV12 transfer = pacs009.getFICdtTrf();
        if (transfer == null) {
            throw new MessageParsingException("pacs.009 has no FICdtTrf element");
        }

        String msgId = transfer.getGrpHdr() != null ? transfer.getGrpHdr().getMsgId() : null;
        List<InternalPaymentInstruction> instructions = new ArrayList<>();

        List<CreditTransferTransaction67> txns = transfer.getCdtTrfTxInf();
        if (txns == null || txns.isEmpty()) {
            throw new MessageParsingException("pacs.009 has no credit transfer transactions");
        }

        for (CreditTransferTransaction67 txn : txns) {
            InternalPaymentInstruction instruction = InternalPaymentInstruction.builder()
                    .messageId(msgId)
                    .instructionId(txn.getPmtId() != null ? txn.getPmtId().getInstrId() : null)
                    .endToEndId(txn.getPmtId() != null ? txn.getPmtId().getEndToEndId() : null)
                    .transactionId(txn.getPmtId() != null ? txn.getPmtId().getTxId() : null)
                    .debtorName(extractFiBic(txn.getDbtr()))
                    .debtorAccountIban(extractIban(txn.getDbtrAcct()))
                    .debtorAccountOther(extractAccountId(txn.getDbtrAcct()))
                    .debtorAgentBic(extractFiBic(txn.getDbtr()))
                    .creditorName(extractFiBic(txn.getCdtr()))
                    .creditorAccountIban(extractIban(txn.getCdtrAcct()))
                    .creditorAccountOther(extractAccountId(txn.getCdtrAcct()))
                    .creditorAgentBic(extractFiBic(txn.getCdtr()))
                    .amount(txn.getIntrBkSttlmAmt() != null ? txn.getIntrBkSttlmAmt().getValue() : null)
                    .currency(txn.getIntrBkSttlmAmt() != null ? txn.getIntrBkSttlmAmt().getCcy() : null)
                    .operationType(OperationType.CREDIT_TRANSFER)
                    .originalMessageType("pacs.009")
                    .build();

            instructions.add(instruction);
        }

        log.info("Parsed pacs.009 message {} with {} FI credit transfer transactions", msgId, instructions.size());
        return instructions;
    }

    private String extractFiBic(BranchAndFinancialInstitutionIdentification8 fi) {
        if (fi == null || fi.getFinInstnId() == null) return null;
        if (fi.getFinInstnId().getBICFI() != null) return fi.getFinInstnId().getBICFI();
        if (fi.getFinInstnId().getNm() != null) return fi.getFinInstnId().getNm();
        return null;
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
}

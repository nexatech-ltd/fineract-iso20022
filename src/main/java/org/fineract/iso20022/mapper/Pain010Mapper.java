package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxPain01000108;
import com.prowidesoftware.swift.model.mx.dic.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.fineract.iso20022.model.enums.OperationType;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Pain010Mapper {

    public List<InternalPaymentInstruction> toPaymentInstructions(AbstractMX mx) {
        MxPain01000108 msg = (MxPain01000108) mx;
        MandateAmendmentRequestV08 req = msg.getMndtAmdmntReq();
        List<InternalPaymentInstruction> result = new ArrayList<>();

        String groupMsgId = null;
        if (req.getGrpHdr() != null) {
            groupMsgId = req.getGrpHdr().getMsgId();
        }

        if (req.getUndrlygAmdmntDtls() != null) {
            for (MandateAmendment8 amendment : req.getUndrlygAmdmntDtls()) {
                InternalPaymentInstruction instruction = parseAmendment(amendment, groupMsgId);
                result.add(instruction);
            }
        }

        log.info("Parsed pain.010 mandate amendment: {} amendments from msgId={}", result.size(), groupMsgId);
        return result;
    }

    private InternalPaymentInstruction parseAmendment(MandateAmendment8 amendment, String groupMsgId) {
        String originalMsgId = null;
        if (amendment.getOrgnlMsgInf() != null) {
            originalMsgId = amendment.getOrgnlMsgInf().getMsgId();
        }

        Mandate21 newMandate = amendment.getMndt();
        String mandateId = null;
        BigDecimal amount = null;
        String currency = null;
        String creditorName = null;
        String debtorName = null;
        String creditorAccountIban = null;
        String debtorAccountIban = null;
        String creditorAccountOther = null;
        String debtorAccountOther = null;

        if (newMandate != null) {
            mandateId = newMandate.getMndtId();

            if (newMandate.getColltnAmt() != null) {
                amount = newMandate.getColltnAmt().getValue();
                currency = newMandate.getColltnAmt().getCcy();
            } else if (newMandate.getMaxAmt() != null) {
                amount = newMandate.getMaxAmt().getValue();
                currency = newMandate.getMaxAmt().getCcy();
            }

            creditorName = newMandate.getCdtr() != null ? newMandate.getCdtr().getNm() : null;
            debtorName = newMandate.getDbtr() != null ? newMandate.getDbtr().getNm() : null;
            creditorAccountIban = extractAccountIban(newMandate.getCdtrAcct());
            creditorAccountOther = extractAccountOther(newMandate.getCdtrAcct());
            debtorAccountIban = extractAccountIban(newMandate.getDbtrAcct());
            debtorAccountOther = extractAccountOther(newMandate.getDbtrAcct());
        }

        return InternalPaymentInstruction.builder()
                .messageId(groupMsgId)
                .operationType(OperationType.MANDATE_AMENDMENT)
                .mandateId(mandateId)
                .originalMessageId(originalMsgId)
                .amount(amount)
                .currency(currency)
                .creditorName(creditorName)
                .creditorAccountIban(creditorAccountIban)
                .creditorAccountOther(creditorAccountOther)
                .debtorName(debtorName)
                .debtorAccountIban(debtorAccountIban)
                .debtorAccountOther(debtorAccountOther)
                .originalMessageType("pain.010")
                .build();
    }

    private String extractAccountIban(CashAccount40 acct) {
        if (acct != null && acct.getId() != null) return acct.getId().getIBAN();
        return null;
    }

    private String extractAccountOther(CashAccount40 acct) {
        if (acct != null && acct.getId() != null && acct.getId().getOthr() != null) return acct.getId().getOthr().getId();
        return null;
    }
}

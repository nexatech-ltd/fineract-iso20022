package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxPain00900108;
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
public class Pain009Mapper {

    public List<InternalPaymentInstruction> toPaymentInstructions(AbstractMX mx) {
        MxPain00900108 msg = (MxPain00900108) mx;
        MandateInitiationRequestV08 req = msg.getMndtInitnReq();
        List<InternalPaymentInstruction> result = new ArrayList<>();

        String groupMsgId = null;
        if (req.getGrpHdr() != null) {
            groupMsgId = req.getGrpHdr().getMsgId();
        }

        if (req.getMndt() != null) {
            for (Mandate23 mandate : req.getMndt()) {
                InternalPaymentInstruction instruction = parseMandate(mandate, groupMsgId);
                result.add(instruction);
            }
        }

        log.info("Parsed pain.009 mandate initiation: {} mandates from msgId={}", result.size(), groupMsgId);
        return result;
    }

    private InternalPaymentInstruction parseMandate(Mandate23 mandate, String groupMsgId) {
        String mandateId = null;
        if (mandate.getMndtId() != null && !mandate.getMndtId().isEmpty()) {
            mandateId = mandate.getMndtId().get(0);
        }

        BigDecimal amount = null;
        String currency = null;
        if (mandate.getColltnAmt() != null) {
            amount = mandate.getColltnAmt().getValue();
            currency = mandate.getColltnAmt().getCcy();
        } else if (mandate.getMaxAmt() != null) {
            amount = mandate.getMaxAmt().getValue();
            currency = mandate.getMaxAmt().getCcy();
        } else if (mandate.getFrstColltnAmt() != null) {
            amount = mandate.getFrstColltnAmt().getValue();
            currency = mandate.getFrstColltnAmt().getCcy();
        }

        String frequency = null;
        java.time.LocalDate firstCollectionDate = null;
        java.time.LocalDate finalCollectionDate = null;
        if (mandate.getOcrncs() != null) {
            MandateOccurrences5 occ = mandate.getOcrncs();
            if (occ.getSeqTp() != null) {
                frequency = occ.getSeqTp().name();
            }
            firstCollectionDate = occ.getFrstColltnDt();
            finalCollectionDate = occ.getFnlColltnDt();
        }

        return InternalPaymentInstruction.builder()
                .messageId(groupMsgId)
                .operationType(OperationType.MANDATE_INITIATION)
                .mandateId(mandateId)
                .amount(amount)
                .currency(currency)
                .creditorName(extractPartyName(mandate.getCdtr()))
                .creditorAccountIban(extractAccountIban(mandate.getCdtrAcct()))
                .creditorAccountOther(extractAccountOther(mandate.getCdtrAcct()))
                .creditorAgentBic(extractBic(mandate.getCdtrAgt()))
                .debtorName(extractPartyName(mandate.getDbtr()))
                .debtorAccountIban(extractAccountIban(mandate.getDbtrAcct()))
                .debtorAccountOther(extractAccountOther(mandate.getDbtrAcct()))
                .debtorAgentBic(extractBic(mandate.getDbtrAgt()))
                .collectionDate(firstCollectionDate)
                .originalMessageType("pain.009")
                .remittanceInfo(frequency)
                .build();
    }

    private String extractPartyName(PartyIdentification272 party) {
        return party != null ? party.getNm() : null;
    }

    private String extractAccountIban(CashAccount40 acct) {
        if (acct != null && acct.getId() != null) {
            return acct.getId().getIBAN();
        }
        return null;
    }

    private String extractAccountOther(CashAccount40 acct) {
        if (acct != null && acct.getId() != null && acct.getId().getOthr() != null) {
            return acct.getId().getOthr().getId();
        }
        return null;
    }

    private String extractBic(BranchAndFinancialInstitutionIdentification8 agent) {
        if (agent != null && agent.getFinInstnId() != null) {
            return agent.getFinInstnId().getBICFI();
        }
        return null;
    }
}

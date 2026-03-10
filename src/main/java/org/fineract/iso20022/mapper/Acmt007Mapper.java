package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxAcmt00700105;
import com.prowidesoftware.swift.model.mx.dic.*;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.fineract.iso20022.model.enums.OperationType;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Acmt007Mapper {

    public List<InternalPaymentInstruction> toPaymentInstructions(AbstractMX mx) {
        MxAcmt00700105 msg = (MxAcmt00700105) mx;
        AccountOpeningRequestV05 req = msg.getAcctOpngReq();
        List<InternalPaymentInstruction> result = new ArrayList<>();

        String messageId = null;
        if (req.getRefs() != null && req.getRefs().getMsgId() != null) {
            messageId = req.getRefs().getMsgId().getId();
        }

        String accountName = null;
        String currency = null;
        String iban = null;
        String otherId = null;
        String purpose = null;

        if (req.getAcct() != null) {
            CustomerAccount4 acct = req.getAcct();
            accountName = acct.getNm();
            currency = acct.getCcy();
            purpose = acct.getAcctPurp();
            if (acct.getId() != null) {
                iban = acct.getId().getIBAN();
                if (acct.getId().getOthr() != null) {
                    otherId = acct.getId().getOthr().getId();
                }
            }
        }

        String orgName = null;
        String country = null;
        if (req.getOrg() != null) {
            orgName = req.getOrg().getFullLglNm();
            country = req.getOrg().getCtryOfOpr();
        }

        InternalPaymentInstruction instruction = InternalPaymentInstruction.builder()
                .messageId(messageId)
                .operationType(OperationType.ACCOUNT_OPENING)
                .debtorName(orgName)
                .debtorAccountIban(iban)
                .debtorAccountOther(otherId)
                .currency(currency)
                .originalMessageType("acmt.007")
                .remittanceInfo(accountName)
                .purposeCode(purpose)
                .regulatoryReporting(country)
                .build();

        result.add(instruction);
        log.info("Parsed acmt.007 account opening request: msgId={}, account={}, org={}",
                messageId, iban != null ? iban : otherId, orgName);
        return result;
    }
}

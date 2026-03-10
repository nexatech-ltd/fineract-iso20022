package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxAcmt00800105;
import com.prowidesoftware.swift.model.mx.dic.*;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.fineract.iso20022.model.enums.OperationType;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Acmt008Mapper {

    public List<InternalPaymentInstruction> toPaymentInstructions(AbstractMX mx) {
        MxAcmt00800105 msg = (MxAcmt00800105) mx;
        AccountOpeningAmendmentRequestV05 req = msg.getAcctOpngAmdmntReq();
        List<InternalPaymentInstruction> result = new ArrayList<>();

        String messageId = null;
        if (req.getRefs() != null && req.getRefs().getMsgId() != null) {
            messageId = req.getRefs().getMsgId().getId();
        }

        String accountName = null;
        String currency = null;
        String iban = null;
        String otherId = null;

        if (req.getAcct() != null) {
            CustomerAccount4 acct = req.getAcct();
            accountName = acct.getNm();
            currency = acct.getCcy();
            if (acct.getId() != null) {
                iban = acct.getId().getIBAN();
                if (acct.getId().getOthr() != null) {
                    otherId = acct.getId().getOthr().getId();
                }
            }
        }

        String orgName = null;
        if (req.getOrg() != null) {
            orgName = req.getOrg().getFullLglNm();
        }

        InternalPaymentInstruction instruction = InternalPaymentInstruction.builder()
                .messageId(messageId)
                .operationType(OperationType.ACCOUNT_MODIFICATION)
                .debtorName(orgName)
                .debtorAccountIban(iban)
                .debtorAccountOther(otherId)
                .currency(currency)
                .originalMessageType("acmt.008")
                .remittanceInfo(accountName)
                .build();

        result.add(instruction);
        log.info("Parsed acmt.008 account amendment: msgId={}, account={}", messageId, iban != null ? iban : otherId);
        return result;
    }
}

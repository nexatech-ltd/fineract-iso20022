package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxAcmt01900104;
import com.prowidesoftware.swift.model.mx.dic.*;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.fineract.iso20022.model.enums.OperationType;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Acmt019Mapper {

    public List<InternalPaymentInstruction> toPaymentInstructions(AbstractMX mx) {
        MxAcmt01900104 msg = (MxAcmt01900104) mx;
        AccountClosingRequestV04 req = msg.getAcctClsgReq();
        List<InternalPaymentInstruction> result = new ArrayList<>();

        String messageId = null;
        if (req.getRefs() != null && req.getRefs().getMsgId() != null) {
            messageId = req.getRefs().getMsgId().getId();
        }

        String accountIban = null;
        String accountOther = null;
        String accountName = null;
        String currency = null;

        if (req.getAcctId() != null) {
            AccountForAction2 acctId = req.getAcctId();
            accountName = acctId.getNm();
            currency = acctId.getCcy();
            if (acctId.getId() != null) {
                accountIban = acctId.getId().getIBAN();
                if (acctId.getId().getOthr() != null) {
                    accountOther = acctId.getId().getOthr().getId();
                }
            }
        }

        String transferAccountIban = null;
        String transferAccountOther = null;
        if (req.getBalTrfAcct() != null) {
            AccountForAction1 trfAcct = req.getBalTrfAcct();
            if (trfAcct.getId() != null) {
                transferAccountIban = trfAcct.getId().getIBAN();
                if (trfAcct.getId().getOthr() != null) {
                    transferAccountOther = trfAcct.getId().getOthr().getId();
                }
            }
        }

        InternalPaymentInstruction instruction = InternalPaymentInstruction.builder()
                .messageId(messageId)
                .operationType(OperationType.ACCOUNT_CLOSING)
                .debtorAccountIban(accountIban)
                .debtorAccountOther(accountOther)
                .debtorName(accountName)
                .currency(currency)
                .creditorAccountIban(transferAccountIban)
                .creditorAccountOther(transferAccountOther)
                .originalMessageType("acmt.019")
                .build();

        result.add(instruction);
        log.info("Parsed acmt.019 account closing: msgId={}, account={}", messageId, accountIban != null ? accountIban : accountOther);
        return result;
    }
}

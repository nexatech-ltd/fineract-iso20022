package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxPacs00400114;
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
public class Pacs004Mapper {

    public List<InternalPaymentInstruction> toPaymentInstructions(AbstractMX mx) {
        if (!(mx instanceof MxPacs00400114 pacs004)) {
            throw new MessageParsingException("Expected pacs.004.001.14 but got: " + mx.getMxId().id());
        }

        PaymentReturnV14 ret = pacs004.getPmtRtr();
        if (ret == null) {
            throw new MessageParsingException("pacs.004 has no PmtRtr element");
        }

        String msgId = ret.getGrpHdr() != null ? ret.getGrpHdr().getMsgId() : null;
        String originalMsgId = null;
        if (ret.getOrgnlGrpInf() != null) {
            originalMsgId = ret.getOrgnlGrpInf().getOrgnlMsgId();
        }

        List<InternalPaymentInstruction> instructions = new ArrayList<>();
        List<PaymentTransaction163> txns = ret.getTxInf();

        if (txns != null) {
            for (PaymentTransaction163 txn : txns) {
                String returnReason = null;
                if (txn.getRtrRsnInf() != null && !txn.getRtrRsnInf().isEmpty()) {
                    PaymentReturnReason7 rsn = txn.getRtrRsnInf().getFirst();
                    if (rsn.getRsn() != null) {
                        returnReason = rsn.getRsn().getCd() != null
                                ? rsn.getRsn().getCd() : rsn.getRsn().getPrtry();
                    }
                }

                BigDecimal returnedAmount = txn.getRtrdIntrBkSttlmAmt() != null
                        ? txn.getRtrdIntrBkSttlmAmt().getValue() : null;
                String currency = txn.getRtrdIntrBkSttlmAmt() != null
                        ? txn.getRtrdIntrBkSttlmAmt().getCcy() : null;

                if (returnedAmount == null && txn.getOrgnlIntrBkSttlmAmt() != null) {
                    returnedAmount = txn.getOrgnlIntrBkSttlmAmt().getValue();
                    currency = txn.getOrgnlIntrBkSttlmAmt().getCcy();
                }

                InternalPaymentInstruction instruction = InternalPaymentInstruction.builder()
                        .messageId(msgId)
                        .originalMessageId(originalMsgId)
                        .originalEndToEndId(txn.getOrgnlEndToEndId())
                        .originalTransactionId(txn.getOrgnlTxId())
                        .instructionId(txn.getRtrId())
                        .endToEndId(txn.getOrgnlEndToEndId())
                        .amount(returnedAmount)
                        .currency(currency)
                        .returnReasonCode(returnReason)
                        .operationType(OperationType.RETURN)
                        .originalMessageType("pacs.004")
                        .build();

                instructions.add(instruction);
            }
        }

        log.info("Parsed pacs.004 message {} with {} return instructions", msgId, instructions.size());
        return instructions;
    }
}

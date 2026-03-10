package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxPacs02800106;
import com.prowidesoftware.swift.model.mx.dic.*;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.exception.MessageParsingException;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.fineract.iso20022.model.enums.OperationType;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Pacs028Mapper {

    public List<InternalPaymentInstruction> toPaymentInstructions(AbstractMX mx) {
        if (!(mx instanceof MxPacs02800106 pacs028)) {
            throw new MessageParsingException("Expected pacs.028.001.06 but got: " + mx.getMxId().id());
        }

        FIToFIPaymentStatusRequestV06 req = pacs028.getFIToFIPmtStsReq();
        if (req == null) {
            throw new MessageParsingException("pacs.028 has no FIToFIPmtStsReq element");
        }

        String msgId = req.getGrpHdr() != null ? req.getGrpHdr().getMsgId() : null;
        List<InternalPaymentInstruction> instructions = new ArrayList<>();

        List<OriginalGroupInformation27> grpInfos = req.getOrgnlGrpInf();
        if (grpInfos != null) {
            for (OriginalGroupInformation27 grpInfo : grpInfos) {
                InternalPaymentInstruction instruction = InternalPaymentInstruction.builder()
                        .messageId(msgId)
                        .originalMessageId(grpInfo.getOrgnlMsgId())
                        .operationType(OperationType.STATUS_REQUEST)
                        .originalMessageType("pacs.028")
                        .build();
                instructions.add(instruction);
            }
        }

        List<PaymentTransaction158> txns = req.getTxInf();
        if (txns != null) {
            for (PaymentTransaction158 txn : txns) {
                InternalPaymentInstruction instruction = InternalPaymentInstruction.builder()
                        .messageId(msgId)
                        .originalEndToEndId(extractOriginalEndToEndId(txn))
                        .originalTransactionId(extractOriginalTxId(txn))
                        .operationType(OperationType.STATUS_REQUEST)
                        .originalMessageType("pacs.028")
                        .build();
                instructions.add(instruction);
            }
        }

        log.info("Parsed pacs.028 message {} with {} status requests", msgId, instructions.size());
        return instructions;
    }

    private String extractOriginalEndToEndId(PaymentTransaction158 txn) {
        try {
            java.lang.reflect.Method m = txn.getClass().getMethod("getOrgnlEndToEndId");
            Object result = m.invoke(txn);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractOriginalTxId(PaymentTransaction158 txn) {
        try {
            java.lang.reflect.Method m = txn.getClass().getMethod("getOrgnlTxId");
            Object result = m.invoke(txn);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}

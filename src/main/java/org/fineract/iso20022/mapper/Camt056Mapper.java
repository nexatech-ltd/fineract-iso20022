package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxCamt05600111;
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
public class Camt056Mapper {

    public List<InternalPaymentInstruction> toPaymentInstructions(AbstractMX mx) {
        if (!(mx instanceof MxCamt05600111 camt056)) {
            throw new MessageParsingException("Expected camt.056.001.11 but got: " + mx.getMxId().id());
        }

        FIToFIPaymentCancellationRequestV11 req = camt056.getFIToFIPmtCxlReq();
        if (req == null) {
            throw new MessageParsingException("camt.056 has no FIToFIPmtCxlReq element");
        }

        String caseId = null;
        if (req.getAssgnmt() != null) {
            caseId = req.getAssgnmt().getId();
        }

        List<InternalPaymentInstruction> instructions = new ArrayList<>();
        List<UnderlyingTransaction34> undrlygList = req.getUndrlyg();

        if (undrlygList != null) {
            for (UnderlyingTransaction34 undrlyg : undrlygList) {
                String originalMsgId = null;
                if (undrlyg.getOrgnlGrpInfAndCxl() != null) {
                    originalMsgId = undrlyg.getOrgnlGrpInfAndCxl().getOrgnlMsgId();
                }

                List<PaymentTransaction155> txns = undrlyg.getTxInf();
                if (txns == null) continue;

                for (PaymentTransaction155 txn : txns) {
                    String cancellationReason = null;
                    if (txn.getCxlRsnInf() != null && !txn.getCxlRsnInf().isEmpty()) {
                        PaymentCancellationReason6 cxlRsn = txn.getCxlRsnInf().getFirst();
                        if (cxlRsn.getRsn() != null) {
                            cancellationReason = extractCancellationReasonCode(cxlRsn.getRsn());
                        }
                    }

                    BigDecimal amount = txn.getOrgnlIntrBkSttlmAmt() != null
                            ? txn.getOrgnlIntrBkSttlmAmt().getValue() : null;
                    String currency = txn.getOrgnlIntrBkSttlmAmt() != null
                            ? txn.getOrgnlIntrBkSttlmAmt().getCcy() : null;

                    InternalPaymentInstruction instruction = InternalPaymentInstruction.builder()
                            .messageId(caseId != null ? caseId : txn.getCxlId())
                            .originalMessageId(originalMsgId)
                            .originalEndToEndId(txn.getOrgnlEndToEndId())
                            .originalTransactionId(txn.getOrgnlTxId())
                            .instructionId(txn.getCxlId())
                            .amount(amount)
                            .currency(currency)
                            .cancellationReasonCode(cancellationReason)
                            .operationType(OperationType.CANCELLATION)
                            .originalMessageType("camt.056")
                            .build();

                    instructions.add(instruction);
                }
            }
        }

        log.info("Parsed camt.056 message with {} cancellation requests", instructions.size());
        return instructions;
    }

    private String extractCancellationReasonCode(CancellationReason33Choice rsn) {
        try {
            java.lang.reflect.Method cdMethod = rsn.getClass().getMethod("getCd");
            Object cd = cdMethod.invoke(rsn);
            if (cd != null) return cd.toString();
        } catch (Exception ignored) {}
        try {
            java.lang.reflect.Method prtryMethod = rsn.getClass().getMethod("getPrtry");
            Object prtry = prtryMethod.invoke(rsn);
            if (prtry != null) return prtry.toString();
        } catch (Exception ignored) {}
        return null;
    }
}

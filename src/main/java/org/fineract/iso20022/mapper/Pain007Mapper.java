package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxPain00700111;
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
public class Pain007Mapper {

    public List<InternalPaymentInstruction> toPaymentInstructions(AbstractMX mx) {
        if (!(mx instanceof MxPain00700111 pain007)) {
            throw new MessageParsingException("Expected pain.007.001.11 but got: " + mx.getMxId().id());
        }

        CustomerPaymentReversalV11 reversal = pain007.getCstmrPmtRvsl();
        if (reversal == null) {
            throw new MessageParsingException("pain.007 has no CstmrPmtRvsl element");
        }

        String msgId = reversal.getGrpHdr() != null ? reversal.getGrpHdr().getMsgId() : null;
        String originalMsgId = null;
        String reversalReason = null;

        OriginalGroupHeader16 orgnlGrp = reversal.getOrgnlGrpInf();
        if (orgnlGrp != null) {
            originalMsgId = orgnlGrp.getOrgnlMsgId();
            if (orgnlGrp.getRvslRsnInf() != null && !orgnlGrp.getRvslRsnInf().isEmpty()) {
                PaymentReversalReason9 rsn = orgnlGrp.getRvslRsnInf().getFirst();
                if (rsn.getRsn() != null) {
                    reversalReason = rsn.getRsn().getCd() != null ? rsn.getRsn().getCd() : rsn.getRsn().getPrtry();
                }
            }
        }

        List<InternalPaymentInstruction> instructions = new ArrayList<>();
        List<OriginalPaymentInstruction41> pmtInfs = reversal.getOrgnlPmtInfAndRvsl();

        if (pmtInfs != null) {
            for (OriginalPaymentInstruction41 pmtInf : pmtInfs) {
                List<PaymentTransaction134> txns = pmtInf.getTxInf();
                if (txns == null) continue;

                String localReversalReason = reversalReason;
                if (pmtInf.getRvslRsnInf() != null && !pmtInf.getRvslRsnInf().isEmpty()) {
                    PaymentReversalReason9 rsn = pmtInf.getRvslRsnInf().getFirst();
                    if (rsn.getRsn() != null) {
                        localReversalReason = rsn.getRsn().getCd() != null
                                ? rsn.getRsn().getCd() : rsn.getRsn().getPrtry();
                    }
                }

                for (PaymentTransaction134 txn : txns) {
                    String txReversalReason = localReversalReason;
                    if (txn.getRvslRsnInf() != null && !txn.getRvslRsnInf().isEmpty()) {
                        PaymentReversalReason9 rsn = txn.getRvslRsnInf().getFirst();
                        if (rsn.getRsn() != null) {
                            txReversalReason = rsn.getRsn().getCd() != null
                                    ? rsn.getRsn().getCd() : rsn.getRsn().getPrtry();
                        }
                    }

                    BigDecimal amount = txn.getOrgnlInstdAmt() != null
                            ? txn.getOrgnlInstdAmt().getValue() : null;
                    String currency = txn.getOrgnlInstdAmt() != null
                            ? txn.getOrgnlInstdAmt().getCcy() : null;

                    InternalPaymentInstruction instruction = InternalPaymentInstruction.builder()
                            .messageId(msgId)
                            .originalMessageId(originalMsgId)
                            .originalEndToEndId(txn.getOrgnlEndToEndId())
                            .instructionId(txn.getRvslId())
                            .endToEndId(txn.getOrgnlEndToEndId())
                            .amount(amount)
                            .currency(currency)
                            .reversalReasonCode(txReversalReason)
                            .operationType(OperationType.REVERSAL)
                            .originalMessageType("pain.007")
                            .build();

                    instructions.add(instruction);
                }
            }
        }

        log.info("Parsed pain.007 message {} with {} reversal instructions", msgId, instructions.size());
        return instructions;
    }
}

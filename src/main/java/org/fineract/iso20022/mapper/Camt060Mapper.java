package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxCamt06000107;
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
public class Camt060Mapper {

    public List<InternalPaymentInstruction> toPaymentInstructions(AbstractMX mx) {
        if (!(mx instanceof MxCamt06000107 camt060)) {
            throw new MessageParsingException("Expected camt.060.001.07 but got: " + mx.getMxId().id());
        }

        AccountReportingRequestV07 req = camt060.getAcctRptgReq();
        if (req == null) {
            throw new MessageParsingException("camt.060 has no AcctRptgReq element");
        }

        String msgId = req.getGrpHdr() != null ? req.getGrpHdr().getMsgId() : null;
        List<InternalPaymentInstruction> instructions = new ArrayList<>();

        List<ReportingRequest7> requests = req.getRptgReq();
        if (requests == null || requests.isEmpty()) {
            throw new MessageParsingException("camt.060 has no reporting requests");
        }

        for (ReportingRequest7 rptReq : requests) {
            String accountId = extractAccountId(rptReq.getAcct());
            String reportType = rptReq.getReqdMsgNmId();

            InternalPaymentInstruction instruction = InternalPaymentInstruction.builder()
                    .messageId(msgId)
                    .instructionId(rptReq.getId())
                    .debtorAccountOther(accountId)
                    .debtorAccountIban(extractIban(rptReq.getAcct()))
                    .reportType(reportType)
                    .operationType(OperationType.ACCOUNT_REPORT_REQUEST)
                    .originalMessageType("camt.060")
                    .build();

            instructions.add(instruction);
        }

        log.info("Parsed camt.060 message {} with {} reporting requests", msgId, instructions.size());
        return instructions;
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

package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.MxPain01400111;
import com.prowidesoftware.swift.model.mx.dic.*;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.util.IdGenerator;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Pain014Mapper {

    public String buildActivationStatusReport(String originalMsgId, String originalMsgType,
                                               String status, String reasonCode, String reasonDescription) {
        MxPain01400111 mx = new MxPain01400111();
        CreditorPaymentActivationRequestStatusReportV11 report =
                new CreditorPaymentActivationRequestStatusReportV11();

        GroupHeader111 grpHdr = new GroupHeader111();
        grpHdr.setMsgId(IdGenerator.generateMessageId());
        grpHdr.setCreDtTm(OffsetDateTime.now());
        report.setGrpHdr(grpHdr);

        OriginalGroupInformation32 orgnlGrpInf = new OriginalGroupInformation32();
        orgnlGrpInf.setOrgnlMsgId(originalMsgId);
        orgnlGrpInf.setOrgnlMsgNmId(originalMsgType != null ? originalMsgType : "pain.008");
        orgnlGrpInf.setOrgnlCreDtTm(OffsetDateTime.now());
        orgnlGrpInf.setOrgnlNbOfTxs("1");
        orgnlGrpInf.setGrpSts(status);
        report.setOrgnlGrpInfAndSts(orgnlGrpInf);

        mx.setCdtrPmtActvtnReqStsRpt(report);

        log.info("Built pain.014 activation status for message {}: status={}", originalMsgId, status);
        return mx.message();
    }
}

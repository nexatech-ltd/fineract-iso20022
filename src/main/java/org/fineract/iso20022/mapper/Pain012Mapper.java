package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.MxPain01200104;
import com.prowidesoftware.swift.model.mx.dic.*;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.util.IdGenerator;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Pain012Mapper {

    public String buildAcceptanceReport(String originalMsgId, String mandateId, boolean accepted, String rejectReason) {
        try {
            MxPain01200104 mx = new MxPain01200104();
            MandateAcceptanceReportV04 report = new MandateAcceptanceReportV04();

            GroupHeader47 grpHdr = new GroupHeader47();
            grpHdr.setMsgId(IdGenerator.generateMessageId());
            grpHdr.setCreDtTm(OffsetDateTime.now());
            report.setGrpHdr(grpHdr);

            MandateAcceptance4 acceptance = new MandateAcceptance4();

            OriginalMessageInformation1 orgnlMsg = new OriginalMessageInformation1();
            orgnlMsg.setMsgId(originalMsgId != null ? originalMsgId : "UNKNOWN");
            orgnlMsg.setMsgNmId("pain.009.001.08");
            acceptance.setOrgnlMsgInf(orgnlMsg);

            AcceptanceResult6 result = new AcceptanceResult6();
            result.setAccptd(accepted);
            if (!accepted && rejectReason != null) {
                result.getAddtlRjctRsnInf().add(rejectReason);
            }
            acceptance.setAccptncRslt(result);

            report.getUndrlygAccptncDtls().add(acceptance);
            mx.setMndtAccptncRpt(report);

            String xml = mx.message();
            log.info("Built pain.012 mandate acceptance report: originalMsgId={}, accepted={}", originalMsgId, accepted);
            return xml;
        } catch (Exception e) {
            log.error("Failed to build pain.012: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to build pain.012", e);
        }
    }
}

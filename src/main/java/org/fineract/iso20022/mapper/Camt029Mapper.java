package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.MxCamt02900113;
import com.prowidesoftware.swift.model.mx.dic.*;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.model.entity.PaymentInvestigation;
import org.fineract.iso20022.model.enums.InvestigationStatus;
import org.fineract.iso20022.util.IdGenerator;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Camt029Mapper {

    public String buildResolutionOfInvestigation(PaymentInvestigation investigation) {
        MxCamt02900113 mx = new MxCamt02900113();
        ResolutionOfInvestigationV13 resolution = new ResolutionOfInvestigationV13();

        CaseAssignment6 assgnmt = new CaseAssignment6();
        assgnmt.setId(IdGenerator.generateMessageId());
        assgnmt.setCreDtTm(OffsetDateTime.now());
        resolution.setAssgnmt(assgnmt);

        Case6 rslvdCase = new Case6();
        rslvdCase.setId(investigation.getInvestigationId());
        resolution.setRslvdCase(rslvdCase);

        InvestigationStatus6Choice sts = new InvestigationStatus6Choice();
        if (investigation.getStatus() == InvestigationStatus.ACCEPTED) {
            sts.setConf("CNCL");
        } else if (investigation.getStatus() == InvestigationStatus.REJECTED) {
            sts.setConf("RJCR");
        } else if (investigation.getStatus() == InvestigationStatus.PARTIALLY_ACCEPTED) {
            sts.setConf("PACR");
        } else {
            sts.setConf("PDCR");
        }
        resolution.setSts(sts);

        mx.setRsltnOfInvstgtn(resolution);

        log.info("Built camt.029 resolution for investigation {}: status={}",
                investigation.getInvestigationId(), investigation.getStatus());
        return mx.message();
    }
}

package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.MxPacs00200112;
import com.prowidesoftware.swift.model.mx.dic.*;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.model.dto.PaymentStatusResponse;
import org.fineract.iso20022.model.entity.PaymentMessage;
import org.fineract.iso20022.model.enums.MessageStatus;
import org.fineract.iso20022.util.IdGenerator;
import org.springframework.stereotype.Component;

/**
 * Builds pacs.002 (FI to FI Payment Status Report) messages
 * based on payment processing results.
 */
@Slf4j
@Component
public class Pacs002Mapper {

    public String buildStatusReport(PaymentMessage payment, String statusCode, String reasonDescription) {
        MxPacs00200112 mx = new MxPacs00200112();
        FIToFIPaymentStatusReportV12 report = new FIToFIPaymentStatusReportV12();

        GroupHeader101 grpHdr = new GroupHeader101();
        grpHdr.setMsgId(IdGenerator.generateMessageId());
        grpHdr.setCreDtTm(OffsetDateTime.now());
        report.setGrpHdr(grpHdr);

        OriginalGroupHeader17 orgnlGrpInfAndSts = new OriginalGroupHeader17();
        orgnlGrpInfAndSts.setOrgnlMsgId(payment.getMessageId());
        orgnlGrpInfAndSts.setOrgnlMsgNmId(payment.getMessageType());

        if (payment.getStatus() == MessageStatus.COMPLETED) {
            orgnlGrpInfAndSts.setGrpSts("ACCP");
        } else if (payment.getStatus() == MessageStatus.REJECTED) {
            orgnlGrpInfAndSts.setGrpSts("RJCT");
        }
        report.addOrgnlGrpInfAndSts(orgnlGrpInfAndSts);

        PaymentTransaction130 txInfAndSts = new PaymentTransaction130();
        txInfAndSts.setOrgnlEndToEndId(payment.getEndToEndId());
        txInfAndSts.setOrgnlInstrId(payment.getInstructionId());

        if (payment.getStatus() == MessageStatus.COMPLETED) {
            txInfAndSts.setTxSts("ACSP");
        } else if (payment.getStatus() == MessageStatus.REJECTED || payment.getStatus() == MessageStatus.FAILED) {
            txInfAndSts.setTxSts("RJCT");

            if (statusCode != null) {
                StatusReasonInformation12 stsRsnInf = new StatusReasonInformation12();
                StatusReason6Choice rsn = new StatusReason6Choice();
                rsn.setCd(statusCode);
                stsRsnInf.setRsn(rsn);
                if (reasonDescription != null) {
                    stsRsnInf.addAddtlInf(reasonDescription);
                }
                txInfAndSts.addStsRsnInf(stsRsnInf);
            }
        }

        report.addTxInfAndSts(txInfAndSts);
        mx.setFIToFIPmtStsRpt(report);

        log.info("Built pacs.002 status report for message {}: status={}",
                payment.getMessageId(), payment.getStatus());
        return mx.message();
    }

    public PaymentStatusResponse toStatusResponse(PaymentMessage payment, String statusXml) {
        return PaymentStatusResponse.builder()
                .messageId(IdGenerator.generateMessageId())
                .originalMessageId(payment.getMessageId())
                .status(payment.getStatus().name())
                .endToEndId(payment.getEndToEndId())
                .debtorAccount(payment.getDebtorAccount())
                .creditorAccount(payment.getCreditorAccount())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .fineractTransactionId(payment.getFineractTransactionId())
                .statusXml(statusXml)
                .processedAt(payment.getUpdatedAt())
                .build();
    }
}

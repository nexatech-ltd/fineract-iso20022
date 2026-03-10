package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.MxAcmt01000104;
import com.prowidesoftware.swift.model.mx.dic.*;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.util.IdGenerator;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Acmt010Mapper {

    public String buildAcknowledgement(String originalMsgId, String accountId, String accountName, String currency) {
        try {
            MxAcmt01000104 mx = new MxAcmt01000104();
            AccountRequestAcknowledgementV04 ack = new AccountRequestAcknowledgementV04();

            References5 refs = new References5();
            MessageIdentification1 msgId = new MessageIdentification1();
            msgId.setId(IdGenerator.generateMessageId());
            msgId.setCreDtTm(OffsetDateTime.now());
            refs.setMsgId(msgId);

            MessageIdentification1 prcId = new MessageIdentification1();
            prcId.setId(originalMsgId != null ? originalMsgId : "UNKNOWN");
            refs.setPrcId(prcId);
            ack.setRefs(refs);

            AccountForAction1 acctId = new AccountForAction1();
            AccountIdentification4Choice id = new AccountIdentification4Choice();
            GenericAccountIdentification1 othr = new GenericAccountIdentification1();
            othr.setId(accountId);
            id.setOthr(othr);
            acctId.setId(id);
            if (currency != null) acctId.setCcy(currency);
            ack.getAcctId().add(acctId);

            mx.setAcctReqAck(ack);
            String xml = mx.message();
            log.info("Built acmt.010 acknowledgement for originalMsgId={}, accountId={}", originalMsgId, accountId);
            return xml;
        } catch (Exception e) {
            log.error("Failed to build acmt.010: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to build acmt.010 acknowledgement", e);
        }
    }
}

package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.MxCamt05400110;
import com.prowidesoftware.swift.model.mx.dic.*;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.model.entity.PaymentMessage;
import org.fineract.iso20022.util.IdGenerator;
import org.springframework.stereotype.Component;

/**
 * Builds camt.054 (Bank to Customer Debit/Credit Notification) messages
 * for real-time transaction notifications.
 */
@Slf4j
@Component
public class Camt054Mapper {

    public String buildNotification(PaymentMessage payment, boolean isCredit) {
        MxCamt05400110 mx = new MxCamt05400110();
        BankToCustomerDebitCreditNotificationV10 notification = new BankToCustomerDebitCreditNotificationV10();

        GroupHeader81 grpHdr = new GroupHeader81();
        grpHdr.setMsgId(IdGenerator.generateMessageId());
        grpHdr.setCreDtTm(OffsetDateTime.now());
        notification.setGrpHdr(grpHdr);

        AccountNotification20 ntfctn = new AccountNotification20();
        ntfctn.setId(IdGenerator.generateMessageId());
        ntfctn.setCreDtTm(OffsetDateTime.now());

        CashAccount41 acct = new CashAccount41();
        AccountIdentification4Choice acctId = new AccountIdentification4Choice();
        GenericAccountIdentification1 othr = new GenericAccountIdentification1();
        othr.setId(isCredit ? payment.getCreditorAccount() : payment.getDebtorAccount());
        acctId.setOthr(othr);
        acct.setId(acctId);
        acct.setCcy(payment.getCurrency());
        ntfctn.setAcct(acct);

        ReportEntry12 ntry = new ReportEntry12();

        ActiveOrHistoricCurrencyAndAmount amt = new ActiveOrHistoricCurrencyAndAmount();
        amt.setValue(payment.getAmount());
        amt.setCcy(payment.getCurrency());
        ntry.setAmt(amt);
        ntry.setCdtDbtInd(isCredit ? CreditDebitCode.CRDT : CreditDebitCode.DBIT);

        ntry.setSts(new EntryStatus1Choice());
        ntry.getSts().setCd("BOOK");

        DateAndDateTime2Choice bookgDt = new DateAndDateTime2Choice();
        bookgDt.setDtTm(OffsetDateTime.now());
        ntry.setBookgDt(bookgDt);

        BankTransactionCodeStructure4 bkTxCd = new BankTransactionCodeStructure4();
        ProprietaryBankTransactionCodeStructure1 prtry = new ProprietaryBankTransactionCodeStructure1();
        prtry.setCd(isCredit ? "CREDIT_TRANSFER" : "DEBIT_TRANSFER");
        bkTxCd.setPrtry(prtry);
        ntry.setBkTxCd(bkTxCd);

        ntfctn.addNtry(ntry);
        notification.addNtfctn(ntfctn);
        mx.setBkToCstmrDbtCdtNtfctn(notification);

        log.info("Built camt.054 {} notification for payment {}",
                isCredit ? "credit" : "debit", payment.getMessageId());
        return mx.message();
    }
}

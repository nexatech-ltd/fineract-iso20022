package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.MxCamt05200113;
import com.prowidesoftware.swift.model.mx.dic.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.model.dto.FineractTransaction;
import org.fineract.iso20022.util.IdGenerator;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Camt052Mapper {

    public String buildIntradayReport(String accountId, String accountName, String currency,
                                       BigDecimal openingBalance, BigDecimal closingBalance,
                                       List<FineractTransaction> transactions,
                                       LocalDate fromDate, LocalDate toDate) {

        MxCamt05200113 mx = new MxCamt05200113();
        BankToCustomerAccountReportV13 report = new BankToCustomerAccountReportV13();

        GroupHeader116 grpHdr = new GroupHeader116();
        grpHdr.setMsgId(IdGenerator.generateMessageId());
        grpHdr.setCreDtTm(OffsetDateTime.now());
        report.setGrpHdr(grpHdr);

        AccountReport37 rpt = new AccountReport37();
        rpt.setId(IdGenerator.generateMessageId());
        rpt.setElctrncSeqNb(BigDecimal.ONE);
        rpt.setCreDtTm(OffsetDateTime.now());

        DateTimePeriod1 frToDt = new DateTimePeriod1();
        frToDt.setFrDtTm(fromDate.atStartOfDay().atOffset(ZoneOffset.UTC));
        frToDt.setToDtTm(toDate.atTime(23, 59, 59).atOffset(ZoneOffset.UTC));
        rpt.setFrToDt(frToDt);

        CashAccount43 acct = new CashAccount43();
        AccountIdentification4Choice acctId = new AccountIdentification4Choice();
        GenericAccountIdentification1 othr = new GenericAccountIdentification1();
        othr.setId(accountId);
        acctId.setOthr(othr);
        acct.setId(acctId);
        acct.setCcy(currency);
        rpt.setAcct(acct);

        rpt.addBal(buildBalance(openingBalance, currency, "OPBD", fromDate));
        rpt.addBal(buildBalance(closingBalance, currency, "CLBD", toDate));

        if (transactions != null) {
            for (FineractTransaction txn : transactions) {
                rpt.addNtry(buildEntry(txn, currency));
            }
        }

        report.addRpt(rpt);
        mx.setBkToCstmrAcctRpt(report);

        log.info("Built camt.052 intraday report for account {} with {} entries",
                accountId, transactions != null ? transactions.size() : 0);
        return mx.message();
    }

    private CashBalance8 buildBalance(BigDecimal amount, String currency,
                                       String balTypeCode, LocalDate date) {
        CashBalance8 bal = new CashBalance8();
        BalanceType13 tp = new BalanceType13();
        BalanceType10Choice cdOrPrtry = new BalanceType10Choice();
        cdOrPrtry.setCd(balTypeCode);
        tp.setCdOrPrtry(cdOrPrtry);
        bal.setTp(tp);

        ActiveOrHistoricCurrencyAndAmount amt = new ActiveOrHistoricCurrencyAndAmount();
        amt.setValue(amount.abs());
        amt.setCcy(currency);
        bal.setAmt(amt);
        bal.setCdtDbtInd(amount.signum() >= 0 ? CreditDebitCode.CRDT : CreditDebitCode.DBIT);

        DateAndDateTime2Choice dt = new DateAndDateTime2Choice();
        dt.setDt(date);
        bal.setDt(dt);

        return bal;
    }

    private ReportEntry15 buildEntry(FineractTransaction txn, String currency) {
        ReportEntry15 ntry = new ReportEntry15();

        ActiveOrHistoricCurrencyAndAmount amt = new ActiveOrHistoricCurrencyAndAmount();
        amt.setValue(txn.getAmount().abs());
        amt.setCcy(currency);
        ntry.setAmt(amt);

        boolean isCredit = txn.isDeposit();
        ntry.setCdtDbtInd(isCredit ? CreditDebitCode.CRDT : CreditDebitCode.DBIT);

        ntry.setSts(new EntryStatus1Choice());
        ntry.getSts().setCd("BOOK");

        DateAndDateTime2Choice bookgDt = new DateAndDateTime2Choice();
        if (txn.getDate() != null && txn.getDate().size() >= 3) {
            bookgDt.setDt(LocalDate.of(txn.getDate().get(0), txn.getDate().get(1), txn.getDate().get(2)));
        }
        ntry.setBookgDt(bookgDt);

        BankTransactionCodeStructure4 bkTxCd = new BankTransactionCodeStructure4();
        ProprietaryBankTransactionCodeStructure1 prtry = new ProprietaryBankTransactionCodeStructure1();
        prtry.setCd(txn.getTransactionTypeValue() != null ? txn.getTransactionTypeValue() : "UNKNOWN");
        bkTxCd.setPrtry(prtry);
        ntry.setBkTxCd(bkTxCd);

        return ntry;
    }
}

package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.MxCamt05300110;
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

/**
 * Builds camt.053 (Bank to Customer Statement) messages
 * from Fineract transaction data.
 */
@Slf4j
@Component
public class Camt053Mapper {

    public String buildStatement(String accountId, String accountName, String currency,
                                 BigDecimal openingBalance, BigDecimal closingBalance,
                                 List<FineractTransaction> transactions,
                                 LocalDate fromDate, LocalDate toDate) {

        MxCamt05300110 mx = new MxCamt05300110();
        BankToCustomerStatementV10 stmt = new BankToCustomerStatementV10();

        GroupHeader81 grpHdr = new GroupHeader81();
        grpHdr.setMsgId(IdGenerator.generateMessageId());
        grpHdr.setCreDtTm(OffsetDateTime.now());
        stmt.setGrpHdr(grpHdr);

        AccountStatement11 acctStmt = new AccountStatement11();
        acctStmt.setId(IdGenerator.generateMessageId());
        acctStmt.setElctrncSeqNb(BigDecimal.ONE);
        acctStmt.setCreDtTm(OffsetDateTime.now());

        DateTimePeriod1 frToDt = new DateTimePeriod1();
        frToDt.setFrDtTm(fromDate.atStartOfDay().atOffset(ZoneOffset.UTC));
        frToDt.setToDtTm(toDate.atTime(23, 59, 59).atOffset(ZoneOffset.UTC));
        acctStmt.setFrToDt(frToDt);

        CashAccount41 acctId = new CashAccount41();
        AccountIdentification4Choice id = new AccountIdentification4Choice();
        GenericAccountIdentification1 othr = new GenericAccountIdentification1();
        othr.setId(accountId);
        id.setOthr(othr);
        acctId.setId(id);
        acctId.setCcy(currency);
        acctStmt.setAcct(acctId);

        acctStmt.addBal(buildBalance(openingBalance, currency, "OPBD", fromDate));
        acctStmt.addBal(buildBalance(closingBalance, currency, "CLBD", toDate));

        if (transactions != null) {
            TotalTransactions6 txsSummry = new TotalTransactions6();
            txsSummry.setTtlNtries(new NumberAndSumOfTransactions4());
            txsSummry.getTtlNtries().setNbOfNtries(String.valueOf(transactions.size()));
            acctStmt.setTxsSummry(txsSummry);

            for (FineractTransaction txn : transactions) {
                ReportEntry12 ntry = buildEntry(txn, currency);
                acctStmt.addNtry(ntry);
            }
        }

        stmt.addStmt(acctStmt);
        mx.setBkToCstmrStmt(stmt);

        log.info("Built camt.053 statement for account {} with {} entries",
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

    private ReportEntry12 buildEntry(FineractTransaction txn, String currency) {
        ReportEntry12 ntry = new ReportEntry12();

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

        EntryDetails11 ntryDtls = new EntryDetails11();
        EntryTransaction12 txDtls = new EntryTransaction12();
        ntryDtls.addTxDtls(txDtls);
        ntry.addNtryDtls(ntryDtls);

        return ntry;
    }
}

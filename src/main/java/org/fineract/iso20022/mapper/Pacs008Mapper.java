package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxPacs00800110;
import com.prowidesoftware.swift.model.mx.dic.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.exception.MessageParsingException;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.springframework.stereotype.Component;

/**
 * Maps pacs.008 (FI to FI Customer Credit Transfer) messages.
 */
@Slf4j
@Component
public class Pacs008Mapper {

    public List<InternalPaymentInstruction> toPaymentInstructions(AbstractMX mx) {
        if (!(mx instanceof MxPacs00800110 pacs008)) {
            throw new MessageParsingException(
                    "Expected pacs.008.001.10 message but got: " + mx.getMxId().id());
        }

        FIToFICustomerCreditTransferV10 transfer = pacs008.getFIToFICstmrCdtTrf();
        if (transfer == null) {
            throw new MessageParsingException("pacs.008 message has no FIToFICstmrCdtTrf element");
        }

        String msgId = transfer.getGrpHdr() != null ? transfer.getGrpHdr().getMsgId() : null;
        List<InternalPaymentInstruction> instructions = new ArrayList<>();

        List<CreditTransferTransaction50> txns = transfer.getCdtTrfTxInf();
        if (txns == null || txns.isEmpty()) {
            throw new MessageParsingException("pacs.008 has no credit transfer transactions");
        }

        for (CreditTransferTransaction50 txn : txns) {
            InternalPaymentInstruction instruction = InternalPaymentInstruction.builder()
                    .messageId(msgId)
                    .instructionId(txn.getPmtId() != null ? txn.getPmtId().getInstrId() : null)
                    .endToEndId(txn.getPmtId() != null ? txn.getPmtId().getEndToEndId() : null)
                    .transactionId(txn.getPmtId() != null ? txn.getPmtId().getTxId() : null)
                    .debtorName(extractPartyName(txn.getDbtr()))
                    .debtorAccountIban(extractIban(txn.getDbtrAcct()))
                    .debtorAccountOther(extractAccountId(txn.getDbtrAcct()))
                    .debtorAgentBic(extractAgentBic(txn.getDbtrAgt()))
                    .creditorName(extractPartyName(txn.getCdtr()))
                    .creditorAccountIban(extractIban(txn.getCdtrAcct()))
                    .creditorAccountOther(extractAccountId(txn.getCdtrAcct()))
                    .creditorAgentBic(extractAgentBic(txn.getCdtrAgt()))
                    .amount(extractAmount(txn))
                    .currency(extractCurrency(txn))
                    .remittanceInfo(extractRemittanceInfo(txn.getRmtInf()))
                    .purposeCode(txn.getPurp() != null ? txn.getPurp().getCd() : null)
                    .originalMessageType("pacs.008")
                    .build();

            instructions.add(instruction);
        }

        log.info("Parsed pacs.008 message {} with {} transactions", msgId, instructions.size());
        return instructions;
    }

    public String buildPacs008(List<InternalPaymentInstruction> instructions, String messageId) {
        MxPacs00800110 mx = new MxPacs00800110();
        FIToFICustomerCreditTransferV10 transfer = new FIToFICustomerCreditTransferV10();

        GroupHeader96 grpHdr = new GroupHeader96();
        grpHdr.setMsgId(messageId);
        grpHdr.setCreDtTm(OffsetDateTime.now());
        grpHdr.setNbOfTxs(String.valueOf(instructions.size()));

        SettlementInstruction11 sttlmInf = new SettlementInstruction11();
        sttlmInf.setSttlmMtd(SettlementMethod1Code.INDA);
        grpHdr.setSttlmInf(sttlmInf);

        transfer.setGrpHdr(grpHdr);

        for (InternalPaymentInstruction instr : instructions) {
            CreditTransferTransaction50 txn = new CreditTransferTransaction50();

            PaymentIdentification13 pmtId = new PaymentIdentification13();
            pmtId.setInstrId(instr.getInstructionId());
            pmtId.setEndToEndId(instr.getEndToEndId());
            pmtId.setTxId(instr.getTransactionId());
            txn.setPmtId(pmtId);

            ActiveCurrencyAndAmount intrbkSttlmAmt = new ActiveCurrencyAndAmount();
            intrbkSttlmAmt.setValue(instr.getAmount());
            intrbkSttlmAmt.setCcy(instr.getCurrency());
            txn.setIntrBkSttlmAmt(intrbkSttlmAmt);

            txn.setDbtr(buildPartyIdentification(instr.getDebtorName()));
            txn.setDbtrAcct(buildCashAccount40(instr.getDebtorAccountIban(), instr.getDebtorAccountOther()));
            txn.setDbtrAgt(buildBranchAndAgent(instr.getDebtorAgentBic()));
            txn.setCdtr(buildPartyIdentification(instr.getCreditorName()));
            txn.setCdtrAcct(buildCashAccount40(instr.getCreditorAccountIban(), instr.getCreditorAccountOther()));
            txn.setCdtrAgt(buildBranchAndAgent(instr.getCreditorAgentBic()));

            transfer.addCdtTrfTxInf(txn);
        }

        mx.setFIToFICstmrCdtTrf(transfer);
        return mx.message();
    }

    private String extractPartyName(PartyIdentification135 party) {
        return (party != null) ? party.getNm() : null;
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

    private String extractAgentBic(BranchAndFinancialInstitutionIdentification6 agent) {
        if (agent == null || agent.getFinInstnId() == null) return null;
        return agent.getFinInstnId().getBICFI();
    }

    private BigDecimal extractAmount(CreditTransferTransaction50 txn) {
        if (txn.getIntrBkSttlmAmt() != null) return txn.getIntrBkSttlmAmt().getValue();
        return null;
    }

    private String extractCurrency(CreditTransferTransaction50 txn) {
        if (txn.getIntrBkSttlmAmt() != null) return txn.getIntrBkSttlmAmt().getCcy();
        return null;
    }

    private String extractRemittanceInfo(RemittanceInformation21 rmtInf) {
        if (rmtInf == null || rmtInf.getUstrd() == null || rmtInf.getUstrd().isEmpty()) return null;
        return String.join(" ", rmtInf.getUstrd());
    }

    private PartyIdentification135 buildPartyIdentification(String name) {
        PartyIdentification135 party = new PartyIdentification135();
        party.setNm(name);
        return party;
    }

    private CashAccount40 buildCashAccount40(String iban, String otherId) {
        CashAccount40 account = new CashAccount40();
        AccountIdentification4Choice id = new AccountIdentification4Choice();
        if (iban != null && !iban.isBlank()) {
            id.setIBAN(iban);
        } else if (otherId != null && !otherId.isBlank()) {
            GenericAccountIdentification1 othr = new GenericAccountIdentification1();
            othr.setId(otherId);
            id.setOthr(othr);
        }
        account.setId(id);
        return account;
    }

    private BranchAndFinancialInstitutionIdentification6 buildBranchAndAgent(String bic) {
        if (bic == null) return null;
        BranchAndFinancialInstitutionIdentification6 agent = new BranchAndFinancialInstitutionIdentification6();
        FinancialInstitutionIdentification18 finInstnId = new FinancialInstitutionIdentification18();
        finInstnId.setBICFI(bic);
        agent.setFinInstnId(finInstnId);
        return agent;
    }
}

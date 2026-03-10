package org.fineract.iso20022.mapper;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxPain00100111;
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
 * Maps pain.001 (Customer Credit Transfer Initiation) messages
 * to internal payment instructions and back.
 */
@Slf4j
@Component
public class Pain001Mapper {

    public List<InternalPaymentInstruction> toPaymentInstructions(AbstractMX mx) {
        if (!(mx instanceof MxPain00100111 pain001)) {
            throw new MessageParsingException(
                    "Expected pain.001.001.11 message but got: " + mx.getMxId().id());
        }

        CustomerCreditTransferInitiationV11 initiation = pain001.getCstmrCdtTrfInitn();
        if (initiation == null) {
            throw new MessageParsingException("pain.001 message has no CstmrCdtTrfInitn element");
        }

        String msgId = initiation.getGrpHdr() != null ? initiation.getGrpHdr().getMsgId() : null;
        List<InternalPaymentInstruction> instructions = new ArrayList<>();

        List<PaymentInstruction40> pmtInfs = initiation.getPmtInf();
        if (pmtInfs == null || pmtInfs.isEmpty()) {
            throw new MessageParsingException("pain.001 has no payment instructions");
        }

        for (PaymentInstruction40 pmtInf : pmtInfs) {
            String debtorName = extractPartyName(pmtInf.getDbtr());
            String debtorAccount = extractAccountId(pmtInf.getDbtrAcct());
            String debtorAgentBic = extractAgentBic(pmtInf.getDbtrAgt());

            List<CreditTransferTransaction54> txns = pmtInf.getCdtTrfTxInf();
            if (txns == null) continue;

            for (CreditTransferTransaction54 txn : txns) {
                InternalPaymentInstruction instruction = InternalPaymentInstruction.builder()
                        .messageId(msgId)
                        .instructionId(txn.getPmtId() != null ? txn.getPmtId().getInstrId() : null)
                        .endToEndId(txn.getPmtId() != null ? txn.getPmtId().getEndToEndId() : null)
                        .debtorName(debtorName)
                        .debtorAccountIban(extractIban(pmtInf.getDbtrAcct()))
                        .debtorAccountOther(debtorAccount)
                        .debtorAgentBic(debtorAgentBic)
                        .creditorName(extractPartyName(txn.getCdtr()))
                        .creditorAccountIban(extractIban(txn.getCdtrAcct()))
                        .creditorAccountOther(extractAccountId(txn.getCdtrAcct()))
                        .creditorAgentBic(extractAgentBic(txn.getCdtrAgt()))
                        .amount(extractAmount(txn.getAmt()))
                        .currency(extractCurrency(txn.getAmt()))
                        .remittanceInfo(extractRemittanceInfo21(txn.getRmtInf()))
                        .purposeCode(txn.getPurp() != null ? txn.getPurp().getCd() : null)
                        .originalMessageType("pain.001")
                        .build();

                instructions.add(instruction);
            }
        }

        log.info("Parsed pain.001 message {} with {} payment instructions", msgId, instructions.size());
        return instructions;
    }

    public String buildPain001(List<InternalPaymentInstruction> instructions, String messageId) {
        MxPain00100111 mx = new MxPain00100111();
        CustomerCreditTransferInitiationV11 initiation = new CustomerCreditTransferInitiationV11();

        GroupHeader95 grpHdr = new GroupHeader95();
        grpHdr.setMsgId(messageId);
        grpHdr.setCreDtTm(OffsetDateTime.now());
        grpHdr.setNbOfTxs(String.valueOf(instructions.size()));
        initiation.setGrpHdr(grpHdr);

        if (!instructions.isEmpty()) {
            PaymentInstruction40 pmtInf = new PaymentInstruction40();
            pmtInf.setPmtInfId(messageId + "-PMT001");
            pmtInf.setPmtMtd(PaymentMethod3Code.TRF);

            InternalPaymentInstruction first = instructions.getFirst();
            pmtInf.setDbtr(buildPartyIdentification(first.getDebtorName()));
            pmtInf.setDbtrAcct(buildCashAccount40(first.getDebtorAccountIban(), first.getDebtorAccountOther()));
            pmtInf.setDbtrAgt(buildBranchAndAgent(first.getDebtorAgentBic()));

            for (InternalPaymentInstruction instr : instructions) {
                CreditTransferTransaction54 txn = new CreditTransferTransaction54();

                PaymentIdentification6 pmtId = new PaymentIdentification6();
                pmtId.setInstrId(instr.getInstructionId());
                pmtId.setEndToEndId(instr.getEndToEndId());
                txn.setPmtId(pmtId);

                AmountType4Choice amt = new AmountType4Choice();
                ActiveOrHistoricCurrencyAndAmount instdAmt = new ActiveOrHistoricCurrencyAndAmount();
                instdAmt.setValue(instr.getAmount());
                instdAmt.setCcy(instr.getCurrency());
                amt.setInstdAmt(instdAmt);
                txn.setAmt(amt);

                txn.setCdtr(buildPartyIdentification(instr.getCreditorName()));
                txn.setCdtrAcct(buildCashAccount40(instr.getCreditorAccountIban(), instr.getCreditorAccountOther()));
                txn.setCdtrAgt(buildBranchAndAgent(instr.getCreditorAgentBic()));

                if (instr.getRemittanceInfo() != null) {
                    RemittanceInformation21 rmtInf = new RemittanceInformation21();
                    rmtInf.addUstrd(instr.getRemittanceInfo());
                    txn.setRmtInf(rmtInf);
                }

                pmtInf.addCdtTrfTxInf(txn);
            }

            initiation.addPmtInf(pmtInf);
        }

        mx.setCstmrCdtTrfInitn(initiation);
        return mx.message();
    }

    private String extractPartyName(PartyIdentification135 party) {
        if (party == null || party.getNm() == null) return null;
        return party.getNm();
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

    private BigDecimal extractAmount(AmountType4Choice amt) {
        if (amt == null) return null;
        if (amt.getInstdAmt() != null) return amt.getInstdAmt().getValue();
        return null;
    }

    private String extractCurrency(AmountType4Choice amt) {
        if (amt == null) return null;
        if (amt.getInstdAmt() != null) return amt.getInstdAmt().getCcy();
        return null;
    }

    private String extractRemittanceInfo21(RemittanceInformation21 rmtInf) {
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

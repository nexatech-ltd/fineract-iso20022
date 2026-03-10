package org.fineract.iso20022.unit.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.fineract.iso20022.exception.MessageParsingException;
import org.fineract.iso20022.mapper.Pacs008Mapper;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class Pacs008MapperTest {

    private Pacs008Mapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new Pacs008Mapper();
    }

    @Test
    void shouldParsePacs008Message() throws Exception {
        String xml = loadSampleXml("sample-pacs008.xml");
        AbstractMX mx = AbstractMX.parse(xml);

        List<InternalPaymentInstruction> instructions = mapper.toPaymentInstructions(mx);

        assertThat(instructions).hasSize(1);

        InternalPaymentInstruction instr = instructions.getFirst();
        assertThat(instr.getMessageId()).isEqualTo("PACS-TEST-001");
        assertThat(instr.getInstructionId()).isEqualTo("PACS-INSTR-001");
        assertThat(instr.getEndToEndId()).isEqualTo("PACS-E2E-001");
        assertThat(instr.getTransactionId()).isEqualTo("TXN-001");
        assertThat(instr.getDebtorName()).isEqualTo("Alpha Corp");
        assertThat(instr.getDebtorAccountOther()).isEqualTo("ACC-DEBTOR-001");
        assertThat(instr.getDebtorAgentBic()).isEqualTo("COBADEFFXXX");
        assertThat(instr.getCreditorName()).isEqualTo("Beta Ltd");
        assertThat(instr.getCreditorAccountOther()).isEqualTo("ACC-CREDITOR-001");
        assertThat(instr.getCreditorAgentBic()).isEqualTo("BNPAFRPPXXX");
        assertThat(instr.getAmount()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(instr.getCurrency()).isEqualTo("USD");
        assertThat(instr.getRemittanceInfo()).isEqualTo("Trade settlement");
        assertThat(instr.getOriginalMessageType()).isEqualTo("pacs.008");
    }

    @Test
    void shouldBuildPacs008Message() {
        InternalPaymentInstruction instr = InternalPaymentInstruction.builder()
                .instructionId("BUILD-INSTR")
                .endToEndId("BUILD-E2E")
                .transactionId("BUILD-TXN")
                .debtorName("Sender Corp")
                .debtorAccountOther("SENDER-ACC")
                .debtorAgentBic("COBADEFFXXX")
                .creditorName("Receiver Inc")
                .creditorAccountOther("RECEIVER-ACC")
                .creditorAgentBic("BNPAFRPPXXX")
                .amount(new BigDecimal("7500.00"))
                .currency("USD")
                .build();

        String xml = mapper.buildPacs008(List.of(instr), "BUILD-PACS-001");

        assertThat(xml).isNotNull();
        assertThat(xml).contains("pacs.008.001.10");
        assertThat(xml).contains("BUILD-PACS-001");
        assertThat(xml).contains("BUILD-E2E");
        assertThat(xml).contains("7500.00");
        assertThat(xml).contains("USD");
    }

    @Test
    void shouldRoundTripPacs008() throws Exception {
        String originalXml = loadSampleXml("sample-pacs008.xml");
        AbstractMX mx = AbstractMX.parse(originalXml);
        List<InternalPaymentInstruction> instructions = mapper.toPaymentInstructions(mx);

        String rebuiltXml = mapper.buildPacs008(instructions, "ROUNDTRIP-PACS-001");

        AbstractMX reparsed = AbstractMX.parse(rebuiltXml);
        assertThat(reparsed).isNotNull();
        assertThat(reparsed.getMxId().id()).startsWith("pacs.008");

        List<InternalPaymentInstruction> reparsedInstructions = mapper.toPaymentInstructions(reparsed);
        assertThat(reparsedInstructions).hasSize(1);
        assertThat(reparsedInstructions.getFirst().getAmount())
                .isEqualByComparingTo(instructions.getFirst().getAmount());
        assertThat(reparsedInstructions.getFirst().getCreditorName())
                .isEqualTo(instructions.getFirst().getCreditorName());
    }

    @Test
    void shouldThrowOnInvalidMessageType() {
        AbstractMX fakeMx = AbstractMX.parse(loadSampleXml("sample-pain001.xml"));

        assertThatThrownBy(() -> mapper.toPaymentInstructions(fakeMx))
                .isInstanceOf(MessageParsingException.class)
                .hasMessageContaining("Expected pacs.008");
    }

    private String loadSampleXml(String filename) {
        try (var is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

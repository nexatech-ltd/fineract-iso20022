package org.fineract.iso20022.unit.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.fineract.iso20022.exception.MessageParsingException;
import org.fineract.iso20022.mapper.Pain001Mapper;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class Pain001MapperTest {

    private Pain001Mapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new Pain001Mapper();
    }

    @Test
    void shouldParsePain001Message() throws Exception {
        String xml = loadSampleXml("sample-pain001.xml");
        AbstractMX mx = AbstractMX.parse(xml);

        List<InternalPaymentInstruction> instructions = mapper.toPaymentInstructions(mx);

        assertThat(instructions).hasSize(1);

        InternalPaymentInstruction instr = instructions.getFirst();
        assertThat(instr.getMessageId()).isEqualTo("MSG-TEST-001");
        assertThat(instr.getInstructionId()).isEqualTo("INSTR-001");
        assertThat(instr.getEndToEndId()).isEqualTo("E2E-001");
        assertThat(instr.getDebtorName()).isEqualTo("John Doe");
        assertThat(instr.getDebtorAccountIban()).isEqualTo("DE89370400440532013000");
        assertThat(instr.getDebtorAgentBic()).isEqualTo("COBADEFFXXX");
        assertThat(instr.getCreditorName()).isEqualTo("Jane Smith");
        assertThat(instr.getCreditorAccountIban()).isEqualTo("FR7630006000011234567890189");
        assertThat(instr.getCreditorAgentBic()).isEqualTo("BNPAFRPPXXX");
        assertThat(instr.getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(instr.getCurrency()).isEqualTo("EUR");
        assertThat(instr.getRemittanceInfo()).isEqualTo("Invoice 12345 payment");
        assertThat(instr.getOriginalMessageType()).isEqualTo("pain.001");
    }

    @Test
    void shouldBuildPain001Message() {
        InternalPaymentInstruction instr = InternalPaymentInstruction.builder()
                .messageId("BUILD-001")
                .instructionId("BUILD-INSTR-001")
                .endToEndId("BUILD-E2E-001")
                .debtorName("Builder Debtor")
                .debtorAccountIban("DE89370400440532013000")
                .debtorAgentBic("COBADEFFXXX")
                .creditorName("Builder Creditor")
                .creditorAccountIban("FR7630006000011234567890189")
                .creditorAgentBic("BNPAFRPPXXX")
                .amount(new BigDecimal("2500.50"))
                .currency("EUR")
                .remittanceInfo("Test payment")
                .build();

        String xml = mapper.buildPain001(List.of(instr), "BUILD-MSG-001");

        assertThat(xml).isNotNull();
        assertThat(xml).contains("pain.001.001.11");
        assertThat(xml).contains("BUILD-MSG-001");
        assertThat(xml).contains("BUILD-INSTR-001");
        assertThat(xml).contains("BUILD-E2E-001");
        assertThat(xml).contains("Builder Debtor");
        assertThat(xml).contains("Builder Creditor");
        assertThat(xml).contains("2500.50");
        assertThat(xml).contains("EUR");
    }

    @Test
    void shouldRoundTripPain001() throws Exception {
        String originalXml = loadSampleXml("sample-pain001.xml");
        AbstractMX mx = AbstractMX.parse(originalXml);
        List<InternalPaymentInstruction> instructions = mapper.toPaymentInstructions(mx);

        String rebuiltXml = mapper.buildPain001(instructions, "ROUNDTRIP-001");

        AbstractMX reparsed = AbstractMX.parse(rebuiltXml);
        assertThat(reparsed).isNotNull();
        assertThat(reparsed.getMxId().id()).startsWith("pain.001");

        List<InternalPaymentInstruction> reparsedInstructions = mapper.toPaymentInstructions(reparsed);
        assertThat(reparsedInstructions).hasSize(1);
        assertThat(reparsedInstructions.getFirst().getAmount())
                .isEqualByComparingTo(instructions.getFirst().getAmount());
    }

    @Test
    void shouldThrowOnInvalidMessage() {
        AbstractMX fakeMx = AbstractMX.parse(loadSampleXml("sample-pacs008.xml"));

        assertThatThrownBy(() -> mapper.toPaymentInstructions(fakeMx))
                .isInstanceOf(MessageParsingException.class)
                .hasMessageContaining("Expected pain.001");
    }

    private String loadSampleXml(String filename) {
        try (var is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null : "Sample file not found: " + filename;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load " + filename, e);
        }
    }
}

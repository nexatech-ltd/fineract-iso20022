package org.fineract.iso20022.unit.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.fineract.iso20022.exception.MessageParsingException;
import org.fineract.iso20022.mapper.Pacs004Mapper;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.fineract.iso20022.model.enums.OperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class Pacs004MapperTest {

    private Pacs004Mapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new Pacs004Mapper();
    }

    @Test
    void shouldParsePacs004Message() throws Exception {
        String xml = loadSampleXml("sample-pacs004.xml");
        AbstractMX mx = AbstractMX.parse(xml);

        List<InternalPaymentInstruction> instructions = mapper.toPaymentInstructions(mx);

        assertThat(instructions).hasSize(1);

        InternalPaymentInstruction instr = instructions.getFirst();
        assertThat(instr.getMessageId()).isEqualTo("RTR-TEST-001");
        assertThat(instr.getReturnReasonCode()).isEqualTo("AC04");
        assertThat(instr.getAmount()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(instr.getCurrency()).isEqualTo("EUR");
        assertThat(instr.getOperationType()).isEqualTo(OperationType.RETURN);
        assertThat(instr.getOriginalMessageType()).isEqualTo("pacs.004");
    }

    @Test
    void shouldThrowOnWrongMessageType() {
        AbstractMX fakeMx = AbstractMX.parse(loadSampleXml("sample-pain001.xml"));

        assertThatThrownBy(() -> mapper.toPaymentInstructions(fakeMx))
                .isInstanceOf(MessageParsingException.class)
                .hasMessageContaining("Expected pacs.004");
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

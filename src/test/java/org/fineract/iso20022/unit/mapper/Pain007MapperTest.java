package org.fineract.iso20022.unit.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.fineract.iso20022.exception.MessageParsingException;
import org.fineract.iso20022.mapper.Pain007Mapper;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.fineract.iso20022.model.enums.OperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class Pain007MapperTest {

    private Pain007Mapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new Pain007Mapper();
    }

    @Test
    void shouldParsePain007Message() throws Exception {
        String xml = loadSampleXml("sample-pain007.xml");
        AbstractMX mx = AbstractMX.parse(xml);

        List<InternalPaymentInstruction> instructions = mapper.toPaymentInstructions(mx);

        assertThat(instructions).hasSize(1);

        InternalPaymentInstruction instr = instructions.getFirst();
        assertThat(instr.getMessageId()).isEqualTo("RVSL-TEST-001");
        assertThat(instr.getOperationType()).isEqualTo(OperationType.REVERSAL);
        assertThat(instr.getOriginalMessageId()).isEqualTo("ORIG-MSG-001");
        assertThat(instr.getReversalReasonCode()).isEqualTo("DUPL");
        assertThat(instr.getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(instr.getCurrency()).isEqualTo("EUR");
        assertThat(instr.getOriginalMessageType()).isEqualTo("pain.007");
    }

    @Test
    void shouldThrowOnWrongMessageType() {
        AbstractMX fakeMx = AbstractMX.parse(loadSampleXml("sample-pain001.xml"));

        assertThatThrownBy(() -> mapper.toPaymentInstructions(fakeMx))
                .isInstanceOf(MessageParsingException.class)
                .hasMessageContaining("Expected pain.007");
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

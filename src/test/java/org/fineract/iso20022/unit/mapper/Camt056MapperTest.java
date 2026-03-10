package org.fineract.iso20022.unit.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.fineract.iso20022.exception.MessageParsingException;
import org.fineract.iso20022.mapper.Camt056Mapper;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.fineract.iso20022.model.enums.OperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class Camt056MapperTest {

    private Camt056Mapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new Camt056Mapper();
    }

    @Test
    void shouldParseCamt056Message() throws Exception {
        String xml = loadSampleXml("sample-camt056.xml");
        AbstractMX mx = AbstractMX.parse(xml);

        List<InternalPaymentInstruction> instructions = mapper.toPaymentInstructions(mx);

        assertThat(instructions).hasSize(1);

        InternalPaymentInstruction instr = instructions.getFirst();
        assertThat(instr.getCancellationReasonCode()).isEqualTo("DUPL");
        assertThat(instr.getOriginalEndToEndId()).isEqualTo("ORIG-E2E-001");
        assertThat(instr.getOperationType()).isEqualTo(OperationType.CANCELLATION);
        assertThat(instr.getAmount()).isEqualByComparingTo(new BigDecimal("2500.00"));
        assertThat(instr.getCurrency()).isEqualTo("EUR");
        assertThat(instr.getOriginalMessageType()).isEqualTo("camt.056");
    }

    @Test
    void shouldThrowOnWrongMessageType() {
        AbstractMX fakeMx = AbstractMX.parse(loadSampleXml("sample-pain001.xml"));

        assertThatThrownBy(() -> mapper.toPaymentInstructions(fakeMx))
                .isInstanceOf(MessageParsingException.class)
                .hasMessageContaining("Expected camt.056");
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

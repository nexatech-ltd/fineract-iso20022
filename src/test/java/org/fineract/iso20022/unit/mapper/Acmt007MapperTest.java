package org.fineract.iso20022.unit.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.fineract.iso20022.mapper.Acmt007Mapper;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.fineract.iso20022.model.enums.OperationType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class Acmt007MapperTest {

    private final Acmt007Mapper mapper = new Acmt007Mapper();

    @Test
    void shouldParseAccountOpeningRequest() {
        String xml = loadSampleXml("sample-acmt007.xml");
        AbstractMX mx = AbstractMX.parse(xml);
        List<InternalPaymentInstruction> instructions = mapper.toPaymentInstructions(mx);

        assertThat(instructions).hasSize(1);
        InternalPaymentInstruction i = instructions.getFirst();
        assertThat(i.getOperationType()).isEqualTo(OperationType.ACCOUNT_OPENING);
        assertThat(i.getMessageId()).isEqualTo("ACMT007-TEST-001");
        assertThat(i.getDebtorAccountIban()).isEqualTo("DE89370400440532013000");
        assertThat(i.getCurrency()).isEqualTo("EUR");
        assertThat(i.getRemittanceInfo()).isEqualTo("Test Savings Account");
        assertThat(i.getDebtorName()).isEqualTo("Test Organization GmbH");
    }

    private String loadSampleXml(String filename) {
        try (var is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}

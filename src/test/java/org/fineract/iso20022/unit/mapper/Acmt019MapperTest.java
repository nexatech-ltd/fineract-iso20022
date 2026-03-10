package org.fineract.iso20022.unit.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.fineract.iso20022.mapper.Acmt019Mapper;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.fineract.iso20022.model.enums.OperationType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class Acmt019MapperTest {

    private final Acmt019Mapper mapper = new Acmt019Mapper();

    @Test
    void shouldParseAccountClosingRequest() {
        String xml = loadSampleXml("sample-acmt019.xml");
        AbstractMX mx = AbstractMX.parse(xml);
        List<InternalPaymentInstruction> instructions = mapper.toPaymentInstructions(mx);

        assertThat(instructions).hasSize(1);
        InternalPaymentInstruction i = instructions.getFirst();
        assertThat(i.getOperationType()).isEqualTo(OperationType.ACCOUNT_CLOSING);
        assertThat(i.getMessageId()).isEqualTo("ACMT019-TEST-001");
        assertThat(i.getDebtorAccountOther()).isEqualTo("12345");
        assertThat(i.getCreditorAccountIban()).isEqualTo("FR7630006000011234567890189");
    }

    private String loadSampleXml(String filename) {
        try (var is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}

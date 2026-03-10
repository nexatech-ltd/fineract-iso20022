package org.fineract.iso20022.unit.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.fineract.iso20022.mapper.Pain010Mapper;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.fineract.iso20022.model.enums.OperationType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class Pain010MapperTest {

    private final Pain010Mapper mapper = new Pain010Mapper();

    @Test
    void shouldParseMandateAmendmentRequest() {
        String xml = loadSampleXml("sample-pain010.xml");
        AbstractMX mx = AbstractMX.parse(xml);
        List<InternalPaymentInstruction> instructions = mapper.toPaymentInstructions(mx);

        assertThat(instructions).hasSize(1);
        InternalPaymentInstruction i = instructions.getFirst();
        assertThat(i.getOperationType()).isEqualTo(OperationType.MANDATE_AMENDMENT);
        assertThat(i.getMandateId()).isEqualTo("MNDT-2025-001");
        assertThat(i.getOriginalMessageId()).isEqualTo("PAIN009-TEST-001");
        assertThat(i.getDebtorName()).isEqualTo("Debtor Person Updated");
        assertThat(i.getCurrency()).isEqualTo("EUR");
    }

    private String loadSampleXml(String filename) {
        try (var is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}

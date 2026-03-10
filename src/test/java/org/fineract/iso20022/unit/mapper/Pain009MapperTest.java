package org.fineract.iso20022.unit.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.fineract.iso20022.mapper.Pain009Mapper;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.fineract.iso20022.model.enums.OperationType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class Pain009MapperTest {

    private final Pain009Mapper mapper = new Pain009Mapper();

    @Test
    void shouldParseMandateInitiationRequest() {
        String xml = loadSampleXml("sample-pain009.xml");
        AbstractMX mx = AbstractMX.parse(xml);
        List<InternalPaymentInstruction> instructions = mapper.toPaymentInstructions(mx);

        assertThat(instructions).hasSize(1);
        InternalPaymentInstruction i = instructions.getFirst();
        assertThat(i.getOperationType()).isEqualTo(OperationType.MANDATE_INITIATION);
        assertThat(i.getMandateId()).isEqualTo("MNDT-2025-001");
        assertThat(i.getCreditorName()).isEqualTo("Creditor Corp");
        assertThat(i.getDebtorName()).isEqualTo("Debtor Person");
        assertThat(i.getCreditorAccountIban()).isEqualTo("DE89370400440532013000");
        assertThat(i.getDebtorAccountIban()).isEqualTo("FR7630006000011234567890189");
        assertThat(i.getCreditorAgentBic()).isEqualTo("COBADEFFXXX");
        assertThat(i.getDebtorAgentBic()).isEqualTo("BNPAFRPPXXX");
        assertThat(i.getCurrency()).isEqualTo("EUR");
    }

    private String loadSampleXml(String filename) {
        try (var is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}

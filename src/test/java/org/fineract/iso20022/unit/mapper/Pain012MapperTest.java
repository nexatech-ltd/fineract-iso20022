package org.fineract.iso20022.unit.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.fineract.iso20022.mapper.Pain012Mapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class Pain012MapperTest {

    private final Pain012Mapper mapper = new Pain012Mapper();

    @Test
    void shouldBuildAcceptanceReport() {
        String xml = mapper.buildAcceptanceReport("ORIG-MSG-001", "MNDT-001", true, null);
        assertThat(xml).contains("pain.012");
        assertThat(xml).contains("ORIG-MSG-001");
    }

    @Test
    void shouldBuildRejectionReport() {
        String xml = mapper.buildAcceptanceReport("ORIG-MSG-002", "MNDT-002", false, "Insufficient funds");
        assertThat(xml).contains("pain.012");
        assertThat(xml).contains("Insufficient funds");
    }
}

package org.fineract.iso20022.unit.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import org.fineract.iso20022.mapper.Pain014Mapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class Pain014MapperTest {

    private Pain014Mapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new Pain014Mapper();
    }

    @Test
    void shouldBuildActivationStatusReport() {
        String xml = mapper.buildActivationStatusReport(
                "ORIG-MSG-001",
                "pain.008",
                "ACCP",
                "AC01",
                "Account blocked");

        assertThat(xml).isNotNull();
        assertThat(xml).contains("pain.014");

        AbstractMX parsed = AbstractMX.parse(xml);
        assertThat(parsed).isNotNull();
        assertThat(parsed.getMxId().id()).startsWith("pain.014");
    }

    @Test
    void shouldBuildActivationStatusReportWithMinimalParams() {
        String xml = mapper.buildActivationStatusReport(
                "ORIG-MSG-002",
                null,
                "RJCT",
                "AM04",
                "Invalid mandate");

        assertThat(xml).isNotNull();
        assertThat(xml).contains("pain.014");
    }
}

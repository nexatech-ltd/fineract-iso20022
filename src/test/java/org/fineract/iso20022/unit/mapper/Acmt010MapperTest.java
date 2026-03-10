package org.fineract.iso20022.unit.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.fineract.iso20022.mapper.Acmt010Mapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class Acmt010MapperTest {

    private final Acmt010Mapper mapper = new Acmt010Mapper();

    @Test
    void shouldBuildAcknowledgement() {
        String xml = mapper.buildAcknowledgement("ORIG-001", "12345", "Test Account", "EUR");
        assertThat(xml).contains("acmt.010");
        assertThat(xml).contains("12345");
    }
}

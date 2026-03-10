package org.fineract.iso20022.unit.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import org.fineract.iso20022.mapper.Camt029Mapper;
import org.fineract.iso20022.model.entity.PaymentInvestigation;
import org.fineract.iso20022.model.enums.InvestigationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class Camt029MapperTest {

    private Camt029Mapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new Camt029Mapper();
    }

    @Test
    void shouldBuildResolutionOfInvestigation() {
        PaymentInvestigation investigation = PaymentInvestigation.builder()
                .investigationId("INV-001")
                .originalMessageId("ORIG-MSG-001")
                .originalEndToEndId("E2E-001")
                .status(InvestigationStatus.ACCEPTED)
                .build();

        String xml = mapper.buildResolutionOfInvestigation(investigation);

        assertThat(xml).isNotNull();
        assertThat(xml).contains("camt.029");

        AbstractMX parsed = AbstractMX.parse(xml);
        assertThat(parsed).isNotNull();
        assertThat(parsed.getMxId().id()).startsWith("camt.029");
    }

    @Test
    void shouldBuildResolutionWithRejectedStatus() {
        PaymentInvestigation investigation = PaymentInvestigation.builder()
                .investigationId("INV-002")
                .originalMessageId("ORIG-MSG-002")
                .status(InvestigationStatus.REJECTED)
                .build();

        String xml = mapper.buildResolutionOfInvestigation(investigation);

        assertThat(xml).isNotNull();
        assertThat(xml).contains("camt.029");
    }
}

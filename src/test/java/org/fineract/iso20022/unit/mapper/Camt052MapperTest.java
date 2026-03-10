package org.fineract.iso20022.unit.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.fineract.iso20022.mapper.Camt052Mapper;
import org.fineract.iso20022.model.dto.FineractTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class Camt052MapperTest {

    private Camt052Mapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new Camt052Mapper();
    }

    @Test
    void shouldBuildIntradayReportWithTestData() {
        FineractTransaction txn = FineractTransaction.builder()
                .id(1L)
                .transactionType("DEPOSIT")
                .amount(new BigDecimal("500.00"))
                .date(List.of(2025, 1, 15))
                .runningBalance(new BigDecimal("1500.00"))
                .build();

        String xml = mapper.buildIntradayReport(
                "ACC-001",
                "Test Account",
                "EUR",
                new BigDecimal("1000.00"),
                new BigDecimal("1500.00"),
                List.of(txn),
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 31));

        assertThat(xml).isNotNull();
        assertThat(xml).contains("camt.052");

        AbstractMX parsed = AbstractMX.parse(xml);
        assertThat(parsed).isNotNull();
        assertThat(parsed.getMxId().id()).startsWith("camt.052");
    }

    @Test
    void shouldBuildIntradayReportWithNoTransactions() {
        String xml = mapper.buildIntradayReport(
                "ACC-002",
                "Empty Account",
                "USD",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(),
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 31));

        assertThat(xml).isNotNull();
        assertThat(xml).contains("camt.052");

        AbstractMX parsed = AbstractMX.parse(xml);
        assertThat(parsed).isNotNull();
    }
}

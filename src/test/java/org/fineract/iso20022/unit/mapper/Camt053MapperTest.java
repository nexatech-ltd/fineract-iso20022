package org.fineract.iso20022.unit.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.fineract.iso20022.mapper.Camt053Mapper;
import org.fineract.iso20022.model.dto.FineractTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class Camt053MapperTest {

    private Camt053Mapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new Camt053Mapper();
    }

    @Test
    void shouldBuildStatementWithTransactions() {
        FineractTransaction deposit = FineractTransaction.builder()
                .id(1L)
                .transactionType("DEPOSIT")
                .amount(new BigDecimal("1000.00"))
                .date(List.of(2025, 1, 15))
                .runningBalance(new BigDecimal("1000.00"))
                .build();

        FineractTransaction withdrawal = FineractTransaction.builder()
                .id(2L)
                .transactionType("WITHDRAWAL")
                .amount(new BigDecimal("200.00"))
                .date(List.of(2025, 1, 16))
                .runningBalance(new BigDecimal("800.00"))
                .build();

        String xml = mapper.buildStatement(
                "ACC-001", "Test Client", "USD",
                BigDecimal.ZERO, new BigDecimal("800.00"),
                List.of(deposit, withdrawal),
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertThat(xml).isNotNull();
        assertThat(xml).contains("camt.053.001.10");
        assertThat(xml).contains("ACC-001");
        assertThat(xml).contains("USD");
        assertThat(xml).contains("OPBD");
        assertThat(xml).contains("CLBD");
        assertThat(xml).contains("CRDT");
        assertThat(xml).contains("DBIT");
        assertThat(xml).contains("1000.00");
        assertThat(xml).contains("200.00");

        AbstractMX parsed = AbstractMX.parse(xml);
        assertThat(parsed).isNotNull();
        assertThat(parsed.getMxId().id()).startsWith("camt.053");
    }

    @Test
    void shouldBuildStatementWithNoTransactions() {
        String xml = mapper.buildStatement(
                "ACC-002", "Empty Account", "EUR",
                BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(),
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertThat(xml).isNotNull();
        assertThat(xml).contains("camt.053");
        assertThat(xml).contains("ACC-002");

        AbstractMX parsed = AbstractMX.parse(xml);
        assertThat(parsed).isNotNull();
    }

    @Test
    void shouldSetCorrectBalanceTypes() {
        String xml = mapper.buildStatement(
                "ACC-003", "Balance Test", "GBP",
                new BigDecimal("500.00"), new BigDecimal("1500.00"),
                List.of(),
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertThat(xml).contains("OPBD");
        assertThat(xml).contains("CLBD");
        assertThat(xml).contains("500.00");
        assertThat(xml).contains("1500.00");
    }
}

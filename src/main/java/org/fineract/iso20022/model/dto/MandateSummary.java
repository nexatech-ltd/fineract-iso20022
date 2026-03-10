package org.fineract.iso20022.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fineract.iso20022.model.entity.DirectDebitMandate;
import org.fineract.iso20022.security.DataMaskingUtil;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MandateSummary {

    private String mandateId;
    private String creditorName;
    private String creditorAccount;
    private String debtorName;
    private String debtorAccount;
    private BigDecimal maxAmount;
    private String currency;
    private String frequency;
    private String status;
    private LocalDate validFrom;
    private LocalDate validTo;
    private LocalDateTime createdAt;

    public static MandateSummary fromEntity(DirectDebitMandate m) {
        return MandateSummary.builder()
                .mandateId(m.getMandateId())
                .creditorName(DataMaskingUtil.maskName(m.getCreditorName()))
                .creditorAccount(DataMaskingUtil.maskAccount(m.getCreditorAccount()))
                .debtorName(DataMaskingUtil.maskName(m.getDebtorName()))
                .debtorAccount(DataMaskingUtil.maskAccount(m.getDebtorAccount()))
                .maxAmount(m.getMaxAmount())
                .currency(m.getCurrency())
                .frequency(m.getFrequency())
                .status(m.getStatus() != null ? m.getStatus().name() : null)
                .validFrom(m.getValidFrom())
                .validTo(m.getValidTo())
                .createdAt(m.getCreatedAt())
                .build();
    }
}

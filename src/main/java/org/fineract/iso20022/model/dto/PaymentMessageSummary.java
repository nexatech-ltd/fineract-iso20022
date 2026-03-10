package org.fineract.iso20022.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fineract.iso20022.model.entity.PaymentMessage;
import org.fineract.iso20022.security.DataMaskingUtil;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMessageSummary {

    private String messageId;
    private String messageType;
    private String direction;
    private String status;
    private String operationType;
    private String endToEndId;
    private String debtorAccount;
    private String creditorAccount;
    private BigDecimal amount;
    private String currency;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PaymentMessageSummary fromEntity(PaymentMessage pm) {
        return PaymentMessageSummary.builder()
                .messageId(pm.getMessageId())
                .messageType(pm.getMessageType())
                .direction(pm.getDirection() != null ? pm.getDirection().name() : null)
                .status(pm.getStatus() != null ? pm.getStatus().name() : null)
                .operationType(pm.getOperationType())
                .endToEndId(pm.getEndToEndId())
                .debtorAccount(DataMaskingUtil.maskAccount(pm.getDebtorAccount()))
                .creditorAccount(DataMaskingUtil.maskAccount(pm.getCreditorAccount()))
                .amount(pm.getAmount())
                .currency(pm.getCurrency())
                .errorMessage(pm.getErrorMessage())
                .createdAt(pm.getCreatedAt())
                .updatedAt(pm.getUpdatedAt())
                .build();
    }
}

package org.fineract.iso20022.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentStatusResponse {

    private String messageId;
    private String originalMessageId;
    private String status;
    private String statusReasonCode;
    private String statusReasonDescription;
    private String endToEndId;
    private String debtorAccount;
    private String creditorAccount;
    private BigDecimal amount;
    private String currency;
    private String fineractTransactionId;
    private String statusXml;
    private LocalDateTime processedAt;
}

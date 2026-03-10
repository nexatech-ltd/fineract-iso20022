package org.fineract.iso20022.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentInitiationRequest {

    @NotBlank(message = "ISO 20022 XML message is required")
    @Size(max = 5242880, message = "XML message exceeds maximum allowed size")
    private String xmlMessage;

    @Size(max = 128, message = "Idempotency key is too long")
    private String idempotencyKey;
}

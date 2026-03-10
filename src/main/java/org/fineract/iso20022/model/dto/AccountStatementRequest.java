package org.fineract.iso20022.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountStatementRequest {

    @NotBlank(message = "Account identifier is required")
    @Size(max = 64, message = "Account identifier too long")
    private String accountId;

    private LocalDate fromDate;
    private LocalDate toDate;

    @Pattern(regexp = "^(camt\\.052|camt\\.053)?$", message = "Invalid statement format")
    private String statementFormat;
}

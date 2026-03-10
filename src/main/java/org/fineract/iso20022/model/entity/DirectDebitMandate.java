package org.fineract.iso20022.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;
import org.fineract.iso20022.model.enums.MandateStatus;
import org.fineract.iso20022.security.EncryptionConverter;

@Entity
@Table(name = "direct_debit_mandates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectDebitMandate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mandate_id", nullable = false, unique = true)
    private String mandateId;

    @Column(name = "creditor_name")
    private String creditorName;

    @Convert(converter = EncryptionConverter.class)
    @Column(name = "creditor_account", nullable = false, length = 255)
    private String creditorAccount;

    @Column(name = "creditor_agent_bic")
    private String creditorAgentBic;

    @Column(name = "debtor_name")
    private String debtorName;

    @Convert(converter = EncryptionConverter.class)
    @Column(name = "debtor_account", nullable = false, length = 255)
    private String debtorAccount;

    @Column(name = "debtor_agent_bic")
    private String debtorAgentBic;

    @Column(name = "max_amount", precision = 19, scale = 4)
    private BigDecimal maxAmount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "frequency")
    private String frequency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MandateStatus status;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "fineract_standing_instruction_id")
    private String fineractStandingInstructionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

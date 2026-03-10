package org.fineract.iso20022.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.fineract.iso20022.security.EncryptionConverter;

@Entity
@Table(name = "account_mapping")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = EncryptionConverter.class)
    @Column(name = "iban", length = 255)
    private String iban;

    @Column(name = "bic", length = 11)
    private String bic;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "fineract_account_id", nullable = false)
    private String fineractAccountId;

    // "SAVINGS" or "LOAN"
    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType;

    @Column(name = "account_holder_name")
    private String accountHolderName;

    @Column(name = "currency", length = 3)
    private String currency;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

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

package org.fineract.iso20022.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;
import org.fineract.iso20022.model.enums.MessageDirection;
import org.fineract.iso20022.model.enums.MessageStatus;

@Entity
@Table(name = "payment_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @Column(name = "message_id", nullable = false, unique = true)
    private String messageId;

    @Column(name = "message_type", nullable = false, length = 50)
    private String messageType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageStatus status;

    @JsonIgnore
    @Column(name = "raw_xml", nullable = false, columnDefinition = "LONGTEXT")
    private String rawXml;

    @Column(name = "end_to_end_id")
    private String endToEndId;

    @Column(name = "instruction_id")
    private String instructionId;

    @Column(name = "debtor_name")
    private String debtorName;

    @Column(name = "debtor_account")
    private String debtorAccount;

    @Column(name = "creditor_name")
    private String creditorName;

    @Column(name = "creditor_account")
    private String creditorAccount;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Column(name = "operation_type", length = 30)
    private String operationType;

    @JsonIgnore
    @Column(name = "fineract_transaction_id")
    private String fineractTransactionId;

    @Column(name = "original_message_ref")
    private String originalMessageRef;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

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

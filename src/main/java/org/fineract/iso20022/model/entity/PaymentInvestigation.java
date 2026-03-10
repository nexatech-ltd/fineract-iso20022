package org.fineract.iso20022.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.fineract.iso20022.model.enums.InvestigationStatus;

@Entity
@Table(name = "payment_investigations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentInvestigation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "investigation_id", nullable = false, unique = true)
    private String investigationId;

    @Column(name = "original_message_id", nullable = false)
    private String originalMessageId;

    @Column(name = "original_end_to_end_id")
    private String originalEndToEndId;

    @Column(name = "original_transaction_id")
    private String originalTransactionId;

    @Column(name = "cancellation_reason_code")
    private String cancellationReasonCode;

    @Column(name = "cancellation_reason_description", columnDefinition = "TEXT")
    private String cancellationReasonDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvestigationStatus status;

    @Column(name = "resolution_xml", columnDefinition = "LONGTEXT")
    private String resolutionXml;

    @Column(name = "fineract_undo_transaction_id")
    private String fineractUndoTransactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_payment_id")
    private PaymentMessage originalPayment;

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

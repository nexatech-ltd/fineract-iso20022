package org.fineract.iso20022.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "system_audit_log", indexes = {
        @Index(name = "idx_sal_event_type", columnList = "eventType"),
        @Index(name = "idx_sal_source", columnList = "sourceComponent"),
        @Index(name = "idx_sal_resource", columnList = "resourceType, resourceId"),
        @Index(name = "idx_sal_created", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String eventType;

    @Column(nullable = false, length = 60)
    private String eventAction;

    @Column(nullable = false, length = 80)
    private String sourceComponent;

    @Column(length = 40)
    private String resourceType;

    @Column(length = 255)
    private String resourceId;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

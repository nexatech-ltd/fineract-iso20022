package org.fineract.iso20022.model.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fineract.iso20022.model.entity.MessageAuditLog;
import org.fineract.iso20022.model.entity.SystemAuditLog;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntry {

    private String action;
    private String details;
    private LocalDateTime createdAt;

    // system-level fields
    private String eventType;
    private String eventAction;
    private String sourceComponent;
    private String resourceType;
    private String resourceId;

    public static AuditLogEntry fromMessageAudit(MessageAuditLog log) {
        return AuditLogEntry.builder()
                .action(log.getAction())
                .details(log.getDetails())
                .createdAt(log.getCreatedAt())
                .build();
    }

    public static AuditLogEntry fromSystemAudit(SystemAuditLog log) {
        return AuditLogEntry.builder()
                .eventType(log.getEventType())
                .eventAction(log.getEventAction())
                .sourceComponent(log.getSourceComponent())
                .resourceType(log.getResourceType())
                .resourceId(log.getResourceId())
                .details(log.getDetails())
                .createdAt(log.getCreatedAt())
                .build();
    }
}

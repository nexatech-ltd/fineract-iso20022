package org.fineract.iso20022.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.fineract.iso20022.model.entity.SystemAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemAuditLogRepository extends JpaRepository<SystemAuditLog, Long> {

    Page<SystemAuditLog> findByEventType(String eventType, Pageable pageable);

    Page<SystemAuditLog> findBySourceComponent(String sourceComponent, Pageable pageable);

    List<SystemAuditLog> findByResourceTypeAndResourceId(String resourceType, String resourceId);

    Page<SystemAuditLog> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to, Pageable pageable);
}

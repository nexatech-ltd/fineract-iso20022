package org.fineract.iso20022.repository;

import java.util.List;
import org.fineract.iso20022.model.entity.MessageAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageAuditLogRepository extends JpaRepository<MessageAuditLog, Long> {

    List<MessageAuditLog> findByPaymentMessageIdOrderByCreatedAtAsc(Long paymentMessageId);
}

package org.fineract.iso20022.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.fineract.iso20022.model.entity.PaymentMessage;
import org.fineract.iso20022.model.enums.MessageDirection;
import org.fineract.iso20022.model.enums.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentMessageRepository extends JpaRepository<PaymentMessage, Long> {

    Optional<PaymentMessage> findByMessageId(String messageId);

    Optional<PaymentMessage> findByEndToEndId(String endToEndId);

    List<PaymentMessage> findByStatus(MessageStatus status);

    List<PaymentMessage> findByMessageTypeAndDirection(String messageType, MessageDirection direction);

    Page<PaymentMessage> findByDirection(MessageDirection direction, Pageable pageable);

    @Query("SELECT pm FROM PaymentMessage pm WHERE pm.debtorAccount = :account OR pm.creditorAccount = :account "
            + "ORDER BY pm.createdAt DESC")
    List<PaymentMessage> findByAccount(@Param("account") String account);

    @Query("SELECT pm FROM PaymentMessage pm WHERE pm.createdAt BETWEEN :from AND :to "
            + "AND pm.status = :status ORDER BY pm.createdAt DESC")
    List<PaymentMessage> findByDateRangeAndStatus(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("status") MessageStatus status);

    @Query("SELECT pm FROM PaymentMessage pm WHERE "
            + "(pm.debtorAccount = :accountId OR pm.creditorAccount = :accountId) "
            + "AND pm.createdAt BETWEEN :from AND :to "
            + "AND pm.status = 'COMPLETED' ORDER BY pm.createdAt ASC")
    List<PaymentMessage> findCompletedByAccountAndDateRange(
            @Param("accountId") String accountId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    boolean existsByMessageId(String messageId);

    Optional<PaymentMessage> findByFineractTransactionId(String fineractTransactionId);
}

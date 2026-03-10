package org.fineract.iso20022.repository;

import java.util.List;
import java.util.Optional;
import org.fineract.iso20022.model.entity.PaymentInvestigation;
import org.fineract.iso20022.model.enums.InvestigationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentInvestigationRepository extends JpaRepository<PaymentInvestigation, Long> {

    Optional<PaymentInvestigation> findByInvestigationId(String investigationId);

    Optional<PaymentInvestigation> findByOriginalMessageId(String originalMessageId);

    List<PaymentInvestigation> findByStatus(InvestigationStatus status);
}

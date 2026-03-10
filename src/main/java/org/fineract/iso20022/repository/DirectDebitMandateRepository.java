package org.fineract.iso20022.repository;

import java.util.List;
import java.util.Optional;
import org.fineract.iso20022.model.entity.DirectDebitMandate;
import org.fineract.iso20022.model.enums.MandateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DirectDebitMandateRepository extends JpaRepository<DirectDebitMandate, Long> {

    Optional<DirectDebitMandate> findByMandateId(String mandateId);

    List<DirectDebitMandate> findByDebtorAccountAndStatus(String debtorAccount, MandateStatus status);

    List<DirectDebitMandate> findByCreditorAccountAndStatus(String creditorAccount, MandateStatus status);

    List<DirectDebitMandate> findByStatus(MandateStatus status);
}

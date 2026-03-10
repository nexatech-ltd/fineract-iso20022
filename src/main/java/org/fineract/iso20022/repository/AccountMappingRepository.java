package org.fineract.iso20022.repository;

import java.util.Optional;
import org.fineract.iso20022.model.entity.AccountMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountMappingRepository extends JpaRepository<AccountMapping, Long> {

    Optional<AccountMapping> findByIbanAndActiveTrue(String iban);

    Optional<AccountMapping> findByExternalIdAndActiveTrue(String externalId);

    Optional<AccountMapping> findByFineractAccountIdAndActiveTrue(String fineractAccountId);

    Optional<AccountMapping> findByIbanAndBicAndActiveTrue(String iban, String bic);
}

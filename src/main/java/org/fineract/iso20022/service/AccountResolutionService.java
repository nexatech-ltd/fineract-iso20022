package org.fineract.iso20022.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.exception.PaymentProcessingException;
import org.fineract.iso20022.model.entity.AccountMapping;
import org.fineract.iso20022.repository.AccountMappingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountResolutionService {

    private static final String IBAN_CACHE_PREFIX = "iban:";
    private static final String EXT_ID_CACHE_PREFIX = "extid:";

    private final AccountMappingRepository accountMappingRepository;
    private final FineractClientService fineractClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AuditService auditService;

    @Value("${iso20022.cache.account-cache-ttl:3600}")
    private long cacheTtlSeconds;

    public record ResolvedAccount(String fineractAccountId, String accountType) {}

    /**
     * Resolve IBAN/BIC or other identifier to a Fineract account ID.
     * Checks: 1) Redis cache, 2) local account_mapping DB, 3) Fineract API by external ID.
     */
    public ResolvedAccount resolve(String iban, String bic, String otherId) {
        if (iban != null && !iban.isBlank()) {
            ResolvedAccount cached = getCachedResolution(IBAN_CACHE_PREFIX + iban);
            if (cached != null) {
                auditService.logAccountResolution("CACHE_HIT", iban, "Resolved from cache");
                return cached;
            }

            Optional<AccountMapping> mapping = (bic != null && !bic.isBlank())
                    ? accountMappingRepository.findByIbanAndBicAndActiveTrue(iban, bic)
                    : accountMappingRepository.findByIbanAndActiveTrue(iban);

            if (mapping.isPresent()) {
                ResolvedAccount resolved = toResolved(mapping.get());
                cacheResolution(IBAN_CACHE_PREFIX + iban, resolved);
                auditService.logAccountResolution("DB_HIT", iban, "Resolved from database");
                return resolved;
            }

            ResolvedAccount fromFineract = searchFineractByExternalId(iban);
            if (fromFineract != null) {
                saveMapping(iban, bic, iban, fromFineract);
                cacheResolution(IBAN_CACHE_PREFIX + iban, fromFineract);
                auditService.logAccountResolution("FINERACT_HIT", iban, "Resolved from Fineract API");
                return fromFineract;
            }
        }

        if (otherId != null && !otherId.isBlank()) {
            ResolvedAccount cached = getCachedResolution(EXT_ID_CACHE_PREFIX + otherId);
            if (cached != null) {
                auditService.logAccountResolution("CACHE_HIT", otherId, "Resolved from cache");
                return cached;
            }

            Optional<AccountMapping> mapping = accountMappingRepository.findByExternalIdAndActiveTrue(otherId);
            if (mapping.isPresent()) {
                ResolvedAccount resolved = toResolved(mapping.get());
                cacheResolution(EXT_ID_CACHE_PREFIX + otherId, resolved);
                auditService.logAccountResolution("DB_HIT", otherId, "Resolved from database");
                return resolved;
            }

            ResolvedAccount fromFineract = searchFineractByExternalId(otherId);
            if (fromFineract != null) {
                saveMapping(null, null, otherId, fromFineract);
                cacheResolution(EXT_ID_CACHE_PREFIX + otherId, fromFineract);
                auditService.logAccountResolution("FINERACT_HIT", otherId, "Resolved from Fineract API");
                return fromFineract;
            }

            auditService.logAccountResolution("NOT_FOUND", otherId, "Account not resolved");
            return new ResolvedAccount(otherId, "SAVINGS");
        }

        return null;
    }

    /**
     * Convenience method to resolve from instruction fields.
     */
    public String resolveToFineractId(String iban, String bic, String otherId) {
        ResolvedAccount resolved = resolve(iban, bic, otherId);
        if (resolved == null) {
            throw new PaymentProcessingException(
                    "Cannot resolve account: iban=" + iban + ", otherId=" + otherId);
        }
        return resolved.fineractAccountId();
    }

    public void evictCache(String identifier) {
        redisTemplate.delete(IBAN_CACHE_PREFIX + identifier);
        redisTemplate.delete(EXT_ID_CACHE_PREFIX + identifier);
    }

    private ResolvedAccount searchFineractByExternalId(String externalId) {
        try {
            Map<String, Object> result = fineractClient.searchAccountByExternalId(externalId);
            if (result != null && result.containsKey("id")) {
                String accountId = result.get("id").toString();
                return new ResolvedAccount(accountId, "SAVINGS");
            }
        } catch (Exception e) {
            log.debug("Fineract external ID search failed for {}: {}", externalId, e.getMessage());
        }
        return null;
    }

    private void saveMapping(String iban, String bic, String externalId, ResolvedAccount resolved) {
        try {
            AccountMapping mapping = AccountMapping.builder()
                    .iban(iban)
                    .bic(bic)
                    .externalId(externalId)
                    .fineractAccountId(resolved.fineractAccountId())
                    .accountType(resolved.accountType())
                    .active(true)
                    .build();
            accountMappingRepository.save(mapping);
        } catch (Exception e) {
            log.warn("Failed to save account mapping for {}: {}", externalId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private ResolvedAccount getCachedResolution(String key) {
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof Map) {
            Map<String, String> map = (Map<String, String>) cached;
            return new ResolvedAccount(map.get("fineractAccountId"), map.get("accountType"));
        }
        return null;
    }

    private void cacheResolution(String key, ResolvedAccount resolved) {
        Map<String, String> value = new HashMap<>();
        value.put("fineractAccountId", resolved.fineractAccountId());
        value.put("accountType", resolved.accountType());
        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(cacheTtlSeconds));
    }

    private ResolvedAccount toResolved(AccountMapping mapping) {
        return new ResolvedAccount(mapping.getFineractAccountId(), mapping.getAccountType());
    }
}

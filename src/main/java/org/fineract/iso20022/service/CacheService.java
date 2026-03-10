package org.fineract.iso20022.service;

import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private static final String ACCOUNT_PREFIX = "account:";
    private static final String CLIENT_PREFIX = "client:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final AuditService auditService;

    @Value("${iso20022.cache.account-cache-ttl:3600}")
    private long accountCacheTtlSeconds;

    public void cacheAccountInfo(String accountId, Map<String, Object> accountData) {
        redisTemplate.opsForValue().set(
                ACCOUNT_PREFIX + accountId,
                accountData,
                Duration.ofSeconds(accountCacheTtlSeconds));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getCachedAccountInfo(String accountId) {
        Object cached = redisTemplate.opsForValue().get(ACCOUNT_PREFIX + accountId);
        if (cached instanceof Map) {
            return (Map<String, Object>) cached;
        }
        return null;
    }

    public void cacheClientInfo(String clientId, Map<String, Object> clientData) {
        redisTemplate.opsForValue().set(
                CLIENT_PREFIX + clientId,
                clientData,
                Duration.ofSeconds(accountCacheTtlSeconds));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getCachedClientInfo(String clientId) {
        Object cached = redisTemplate.opsForValue().get(CLIENT_PREFIX + clientId);
        if (cached instanceof Map) {
            return (Map<String, Object>) cached;
        }
        return null;
    }

    public void evictAccountCache(String accountId) {
        redisTemplate.delete(ACCOUNT_PREFIX + accountId);
        auditService.logCacheOperation("EVICTED", accountId, "Account cache evicted");
    }

    public void evictClientCache(String clientId) {
        redisTemplate.delete(CLIENT_PREFIX + clientId);
    }
}

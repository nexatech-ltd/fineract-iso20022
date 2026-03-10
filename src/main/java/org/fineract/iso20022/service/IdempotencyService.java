package org.fineract.iso20022.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final AuditService auditService;

    @Value("${iso20022.cache.idempotency-ttl:86400}")
    private long ttlSeconds;

    /**
     * Check if a request with the given key has already been processed.
     * @return the cached result if exists, null otherwise
     */
    public String checkDuplicate(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        Object cached = redisTemplate.opsForValue().get(KEY_PREFIX + idempotencyKey);
        if (cached != null) {
            log.info("Idempotent duplicate detected for key: {}", idempotencyKey);
            auditService.logIdempotencyCheck("DUPLICATE_FOUND", idempotencyKey, "Existing result found");
            return cached.toString();
        }
        return null;
    }

    /**
     * Store the result for an idempotency key.
     */
    public void storeResult(String idempotencyKey, String result) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + idempotencyKey, result, Duration.ofSeconds(ttlSeconds));
        log.debug("Stored idempotency result for key: {}", idempotencyKey);
    }

    /**
     * Try to acquire a processing lock for the given key.
     * Prevents concurrent processing of duplicate requests.
     */
    public boolean tryLock(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return true;
        }
        String lockKey = KEY_PREFIX + "lock:" + idempotencyKey;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "PROCESSING", Duration.ofMinutes(5));
        if (Boolean.TRUE.equals(acquired)) {
            auditService.logIdempotencyCheck("LOCK_ACQUIRED", idempotencyKey, "Lock set with TTL");
        }
        return Boolean.TRUE.equals(acquired);
    }

    public void releaseLock(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        redisTemplate.delete(KEY_PREFIX + "lock:" + idempotencyKey);
    }
}

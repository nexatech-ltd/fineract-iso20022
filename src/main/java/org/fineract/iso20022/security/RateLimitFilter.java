package org.fineract.iso20022.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final int maxRequestsPerWindow;
    private final long windowMillis;
    private final Map<String, ClientBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${iso20022.security.rate-limit.requests-per-second:50}") int requestsPerSecond,
            @Value("${iso20022.security.rate-limit.window-seconds:1}") int windowSeconds) {
        this.maxRequestsPerWindow = requestsPerSecond * windowSeconds;
        this.windowMillis = windowSeconds * 1000L;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || path.startsWith("/swagger") || path.startsWith("/v3/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = resolveClientKey(request);
        ClientBucket bucket = buckets.computeIfAbsent(clientKey, k -> new ClientBucket());

        if (!bucket.tryConsume()) {
            log.warn("Rate limit exceeded for client: {}", DataMaskingUtil.sanitizeForLog(clientKey));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"RATE_LIMIT_EXCEEDED\","
                    + "\"message\":\"Too many requests. Please retry later.\"}");
            return;
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequestsPerWindow));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.remaining()));
        filterChain.doFilter(request, response);
    }

    private String resolveClientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private class ClientBucket {
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());

        boolean tryConsume() {
            long now = System.currentTimeMillis();
            if (now - windowStart.get() > windowMillis) {
                count.set(0);
                windowStart.set(now);
            }
            return count.incrementAndGet() <= maxRequestsPerWindow;
        }

        int remaining() {
            return Math.max(0, maxRequestsPerWindow - count.get());
        }
    }
}

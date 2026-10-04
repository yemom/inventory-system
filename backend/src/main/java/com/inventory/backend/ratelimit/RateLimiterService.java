package com.inventory.backend.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Distributed rate limiter using Redis token-bucket algorithm.
 * <p>
 * Uses a Lua script for atomic check-and-consume, so multiple API
 * instances share the same rate-limit state via Redis.
 * <p>
 * The Lua script:
 * 1. Gets the current token count for the key
 * 2. If the key doesn't exist, initializes it with full capacity
 * 3. Calculates tokens to add based on elapsed time
 * 4. If enough tokens, consumes one and returns allowed
 * 5. Otherwise returns denied with retry-after
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    @Value("${rate-limit.login.capacity:5}")
    private long loginCapacity;

    @Value("${rate-limit.login.window-seconds:60}")
    private long loginWindowSeconds;

    @Value("${rate-limit.signup.capacity:3}")
    private long signupCapacity;

    @Value("${rate-limit.signup.window-seconds:3600}")
    private long signupWindowSeconds;

    @Value("${rate-limit.api.capacity:120}")
    private long apiCapacity;

    @Value("${rate-limit.api.window-seconds:60}")
    private long apiWindowSeconds;

    @Value("${rate-limit.reports.capacity:20}")
    private long reportsCapacity;

    @Value("${rate-limit.reports.window-seconds:60}")
    private long reportsWindowSeconds;

    /**
     * Lua script for atomic token-bucket rate limiting.
     * Returns: {allowed (1/0), remaining_tokens, retry_after_seconds}
     */
    private static final String TOKEN_BUCKET_LUA = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local window_seconds = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])

            local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
            local tokens = tonumber(bucket[1])
            local last_refill = tonumber(bucket[2])

            if tokens == nil then
                tokens = capacity
                last_refill = now
            end

            -- Calculate tokens to add based on elapsed time
            local elapsed = now - last_refill
            local refill_rate = capacity / window_seconds
            local tokens_to_add = math.floor(elapsed * refill_rate)
            if tokens_to_add > 0 then
                tokens = math.min(capacity, tokens + tokens_to_add)
                last_refill = now
            end

            -- Try to consume tokens
            local allowed = 0
            local retry_after = 0
            if tokens >= requested then
                tokens = tokens - requested
                allowed = 1
            else
                -- Calculate how long to wait for 1 token
                retry_after = math.ceil((requested - tokens) / refill_rate)
            end

            -- Store updated state
            redis.call('HMSET', key, 'tokens', tokens, 'last_refill', last_refill)
            redis.call('EXPIRE', key, window_seconds * 2)

            return {allowed, math.floor(tokens), retry_after}
            """;

    private static final DefaultRedisScript<List> TOKEN_BUCKET_SCRIPT;

    static {
        TOKEN_BUCKET_SCRIPT = new DefaultRedisScript<>(TOKEN_BUCKET_LUA, List.class);
    }

    public enum RateLimitType {
        LOGIN,
        SIGNUP,
        API,
        REPORTS
    }

    public static class RateLimitResult {
        private final boolean allowed;
        private final long remainingTokens;
        private final long retryAfterSeconds;

        public RateLimitResult(boolean allowed, long remainingTokens, long retryAfterSeconds) {
            this.allowed = allowed;
            this.remainingTokens = remainingTokens;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public long getRemainingTokens() {
            return remainingTokens;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    /**
     * Get the capacity for a rate limit type.
     */
    public long getCapacity(RateLimitType type) {
        return switch (type) {
            case LOGIN -> loginCapacity;
            case SIGNUP -> signupCapacity;
            case API -> apiCapacity;
            case REPORTS -> reportsCapacity;
        };
    }

    /**
     * Get the window in seconds for a rate limit type.
     */
    private long getWindowSeconds(RateLimitType type) {
        return switch (type) {
            case LOGIN -> loginWindowSeconds;
            case SIGNUP -> signupWindowSeconds;
            case API -> apiWindowSeconds;
            case REPORTS -> reportsWindowSeconds;
        };
    }

    /**
     * Check if a request is allowed under the given rate limit type.
     *
     * @param key  unique key (e.g., "rate:login:192.168.1.1" or "rate:api:user:42")
     * @param type the rate limit category
     * @return result with allowed flag, remaining tokens, and retry-after
     */
    public RateLimitResult isAllowed(String key, RateLimitType type) {
        try {
            long now = System.currentTimeMillis() / 1000;
            List<Long> result = redisTemplate.execute(
                    TOKEN_BUCKET_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(getCapacity(type)),
                    String.valueOf(getWindowSeconds(type)),
                    String.valueOf(now),
                    "1"
            );

            if (result == null || result.size() < 3) {
                // Redis error — fail open (allow request) to avoid blocking
                // legitimate traffic when Redis is unavailable.
                log.warn("Rate limiter Redis call failed for key={}, failing open", key);
                return new RateLimitResult(true, getCapacity(type), 0);
            }

            boolean allowed = result.get(0) != null && result.get(0) == 1L;
            long remaining = result.get(1) != null ? result.get(1) : 0L;
            long retryAfter = result.get(2) != null ? result.get(2) : 0L;

            return new RateLimitResult(allowed, remaining, retryAfter);
        } catch (Exception e) {
            // Fail open on Redis errors — rate limiting should never block
            // legitimate traffic if Redis is down.
            log.warn("Rate limiter error for key={}, failing open: {}", key, e.getMessage());
            return new RateLimitResult(true, getCapacity(type), 0);
        }
    }
}

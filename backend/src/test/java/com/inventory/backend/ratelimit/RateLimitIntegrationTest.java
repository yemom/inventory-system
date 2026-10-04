package com.inventory.backend.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the distributed token-bucket rate limiter.
 * <p>
 * The Redis Lua script is exercised through a mocked {@link StringRedisTemplate}
 * so the tests do not require a running Redis instance. The tests verify the
 * three behaviours that matter for correctness:
 * <ul>
 *   <li>allowed when the bucket has tokens</li>
 *   <li>denied (with retry-after) when the bucket is empty</li>
 *   <li>fails open when Redis is unavailable</li>
 * </ul>
 */
class RateLimitIntegrationTest {

    private StringRedisTemplate redisTemplate;
    private RateLimiterService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        service = new RateLimiterService(redisTemplate);

        // Populate the @Value configuration that Spring normally injects.
        ReflectionTestUtils.setField(service, "loginCapacity", 5L);
        ReflectionTestUtils.setField(service, "loginWindowSeconds", 60L);
        ReflectionTestUtils.setField(service, "signupCapacity", 3L);
        ReflectionTestUtils.setField(service, "signupWindowSeconds", 3600L);
        ReflectionTestUtils.setField(service, "apiCapacity", 120L);
        ReflectionTestUtils.setField(service, "apiWindowSeconds", 60L);
        ReflectionTestUtils.setField(service, "reportsCapacity", 20L);
        ReflectionTestUtils.setField(service, "reportsWindowSeconds", 60L);
    }

    @Test
    @DisplayName("Allows the request when the bucket has tokens")
    void allowedWhenTokensAvailable() {
        // Lua returns {allowed=1, remaining=4, retryAfter=0}
        doReturn(List.of(1L, 4L, 0L))
                .when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(), any(), any(), any());

        RateLimiterService.RateLimitResult result =
                service.isAllowed("rate:login:ip:127.0.0.1", RateLimiterService.RateLimitType.LOGIN);

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemainingTokens()).isEqualTo(4);
        assertThat(result.getRetryAfterSeconds()).isZero();
    }

    @Test
    @DisplayName("Denies the request with retry-after when the bucket is empty")
    void deniedWhenTokensExhausted() {
        // Lua returns {allowed=0, remaining=0, retryAfter=12}
        doReturn(List.of(0L, 0L, 12L))
                .when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(), any(), any(), any());

        RateLimiterService.RateLimitResult result =
                service.isAllowed("rate:login:ip:127.0.0.1", RateLimiterService.RateLimitType.LOGIN);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getRetryAfterSeconds()).isEqualTo(12);
    }

    @Test
    @DisplayName("Fails open (allows) when Redis is unavailable")
    void failsOpenWhenRedisUnavailable() {
        doThrow(new RuntimeException("Redis connection refused"))
                .when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), any(), any(), any(), any());

        RateLimiterService.RateLimitResult result =
                service.isAllowed("rate:login:ip:127.0.0.1", RateLimiterService.RateLimitType.LOGIN);

        // Availability over strict enforcement: never block legitimate traffic.
        assertThat(result.isAllowed()).isTrue();
    }

    @Test
    @DisplayName("Resolves the correct capacity per endpoint category")
    void resolvesCapacityPerCategory() {
        assertThat(service.getCapacity(RateLimiterService.RateLimitType.LOGIN)).isEqualTo(5);
        assertThat(service.getCapacity(RateLimiterService.RateLimitType.SIGNUP)).isEqualTo(3);
        assertThat(service.getCapacity(RateLimiterService.RateLimitType.API)).isEqualTo(120);
        assertThat(service.getCapacity(RateLimiterService.RateLimitType.REPORTS)).isEqualTo(20);
    }
}

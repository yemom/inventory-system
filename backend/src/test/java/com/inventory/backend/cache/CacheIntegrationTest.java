package com.inventory.backend.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.backend.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the cache-aside {@link CacheService}.
 * <p>
 * Redis is mocked so the tests do not require a running instance.
 * The critical behaviour under test is tenant-safe cache keys: every
 * key must be prefixed with the current tenant ID so one
 * organization can never read another organization's cached data.
 */
class CacheIntegrationTest {

    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOps;
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        cacheService = new CacheService(redisTemplate, new ObjectMapper());

        // Simulate an authenticated user belonging to tenant-A.
        TenantContext.setCurrentTenantId("tenant-A");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("put() stores the value under a tenant-prefixed key")
    void putUsesTenantPrefixedKey() {
        cacheService.put("product:1", "value", 60);

        verify(valueOps).set(
                eq("tenant:tenant-A:product:1"),
                eq("value"),
                any(Duration.class));
    }

    @Test
    @DisplayName("get() reads from the tenant-prefixed key")
    void getUsesTenantPrefixedKey() {
        when(valueOps.get("tenant:tenant-A:product:1")).thenReturn("cached");

        Optional<String> result = cacheService.get("product:1", String.class);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("cached");
    }

    @Test
    @DisplayName("invalidate() deletes the tenant-prefixed key")
    void invalidateUsesTenantPrefixedKey() {
        cacheService.invalidate("product:1");

        verify(redisTemplate).delete("tenant:tenant-A:product:1");
    }

    @Test
    @DisplayName("different tenants never share cache keys")
    void tenantsAreIsolated() {
        cacheService.put("product:1", "value", 60);

        TenantContext.setCurrentTenantId("tenant-B");
        verify(valueOps).set(
                eq("tenant:tenant-A:product:1"),
                eq("value"),
                any(Duration.class));

        // A tenant-B read must not hit tenant-A's key.
        when(valueOps.get("tenant:tenant-B:product:1")).thenReturn(null);
        assertThat(cacheService.get("product:1", String.class)).isEmpty();
    }
}

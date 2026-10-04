package com.inventory.backend.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cache-aside (lazy loading) cache service backed by Redis.
 * <p>
 * Cache keys are tenant-safe: they always include a tenant/organization
 * prefix when tenant context is available, preventing cross-tenant
 * data leakage.
 * <p>
 * If Redis is unavailable, the cache service degrades gracefully —
 * it logs a warning and fetches from the database directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${cache.category.ttl:300}")
    private long categoryTtl;

    @Value("${cache.warehouse.ttl:300}")
    private long warehouseTtl;

    @Value("${cache.dashboard.ttl:30}")
    private long dashboardTtl;

    @Value("${cache.product.ttl:120}")
    private long productTtl;

    /**
     * Get from cache, or load from database and store in cache.
     *
     * @param key        cache key (will be prefixed with tenant if available)
     * @param ttlSeconds TTL in seconds
     * @param type       class type for deserialization
     * @param loader     database loader function
     * @return cached or freshly loaded value
     */
    public <T> T getOrLoad(String key, long ttlSeconds, Class<T> type, Supplier<T> loader) {
        String tenantKey = prefixWithTenant(key);

        try {
            Object cached = redisTemplate.opsForValue().get(tenantKey);
            if (cached != null) {
                log.debug("Cache hit for key={}", tenantKey);
                return type.cast(cached);
            }
        } catch (Exception e) {
            log.warn("Redis get failed for key={}, fetching from DB: {}", tenantKey, e.getMessage());
        }

        // Cache miss — load from database
        T value = loader.get();
        if (value != null) {
            try {
                redisTemplate.opsForValue().set(tenantKey, value, Duration.ofSeconds(ttlSeconds));
                log.debug("Cache set for key={} with ttl={}s", tenantKey, ttlSeconds);
            } catch (Exception e) {
                log.warn("Redis set failed for key={}: {}", tenantKey, e.getMessage());
            }
        }
        return value;
    }

    /**
     * Get from cache without loading (returns empty if not cached).
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        String tenantKey = prefixWithTenant(key);
        try {
            Object cached = redisTemplate.opsForValue().get(tenantKey);
            if (cached != null) {
                return Optional.of(type.cast(cached));
            }
        } catch (Exception e) {
            log.warn("Redis get failed for key={}: {}", tenantKey, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Put a value into the cache.
     */
    public void put(String key, Object value, long ttlSeconds) {
        String tenantKey = prefixWithTenant(key);
        try {
            redisTemplate.opsForValue().set(tenantKey, value, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("Redis set failed for key={}: {}", tenantKey, e.getMessage());
        }
    }

    /**
     * Invalidate a cache entry.
     */
    public void invalidate(String key) {
        String tenantKey = prefixWithTenant(key);
        try {
            redisTemplate.delete(tenantKey);
            log.debug("Cache invalidated for key={}", tenantKey);
        } catch (Exception e) {
            log.warn("Redis delete failed for key={}: {}", tenantKey, e.getMessage());
        }
    }

    /**
     * Invalidate all cache entries matching a pattern.
     */
    public void invalidatePattern(String pattern) {
        String tenantPattern = prefixWithTenant(pattern);
        try {
            var keys = redisTemplate.keys(tenantPattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("Cache invalidated pattern={} ({} keys)", tenantPattern, keys.size());
            }
        } catch (Exception e) {
            log.warn("Redis delete pattern failed for {}: {}", tenantPattern, e.getMessage());
        }
    }

    /**
     * Prefix cache key with tenant/organization ID for data isolation.
     * If no tenant context is available, uses "default" as prefix.
     */
    private String prefixWithTenant(String key) {
        String tenantId = TenantContext.getCurrentTenantId();
        return "tenant:" + tenantId + ":" + key;
    }

    // ── Convenience methods for specific cache types ──────────────────

    public <T> T getCategory(long id, Class<T> type, Supplier<T> loader) {
        return getOrLoad("category:" + id, categoryTtl, type, loader);
    }

    public <T> T getWarehouse(long id, Class<T> type, Supplier<T> loader) {
        return getOrLoad("warehouse:" + id, warehouseTtl, type, loader);
    }

    public <T> T getDashboardStats(String suffix, Class<T> type, Supplier<T> loader) {
        return getOrLoad("dashboard:" + suffix, dashboardTtl, type, loader);
    }

    public <T> T getProduct(long id, Class<T> type, Supplier<T> loader) {
        return getOrLoad("product:" + id, productTtl, type, loader);
    }

    public void invalidateCategory(long id) {
        invalidate("category:" + id);
    }

    public void invalidateWarehouse(long id) {
        invalidate("warehouse:" + id);
    }

    public void invalidateDashboard() {
        invalidatePattern("dashboard:*");
    }

    public void invalidateProduct(long id) {
        invalidate("product:" + id);
    }
}

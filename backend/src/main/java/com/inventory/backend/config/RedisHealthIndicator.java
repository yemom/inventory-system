package com.inventory.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Reports Redis availability <b>without</b> letting it drag the service out of
 * the load balancer.
 *
 * <p>Why this overrides Spring Boot's own {@code RedisHealthIndicator}: Redis is
 * an <em>optional</em> dependency in this application by explicit design. The rate
 * limiter and the cache both fail open — an unreachable Redis degrades
 * functionality, it does not break it. Boot's default indicator reports
 * {@code DOWN}, which makes the aggregate health {@code DOWN}, which makes
 * Render/Kubernetes take the instance out of rotation and return <b>503 to
 * every request, including ones that need no Redis at all</b>.
 *
 * <p>That contradiction is not theoretical: a deployed instance whose Redis
 * service had expired answered {@code {"status":"DOWN"}} and returned 503 for
 * every login, while the API itself was fully functional.
 *
 * <p>So this indicator deliberately reports {@code UP} with a {@code degraded}
 * detail. The information is still visible in the health payload and in the
 * startup log — it simply no longer masquerades as a total outage. The database
 * remains a hard dependency: {@link DatabaseHealthIndicator} still reports
 * {@code DOWN}, which is correct, because without PostgreSQL the API genuinely
 * cannot serve a single request.
 *
 * <p>Registered as the {@code redis} health component, with Boot's auto-
 * configured Redis contributor disabled via
 * {@code management.health.redis.enabled=false}.
 */
@Component("redis")
public class RedisHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(RedisHealthIndicator.class);

    /** Short enough to fail fast — this runs on every health probe. */
    private static final int PROBE_TIMEOUT_SECONDS = 2;

    private final RedisConnectionFactory connectionFactory;

    public RedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        RedisConnection connection = null;
        try {
            connection = connectionFactory.getConnection();
            String pong = connection.ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                return Health.up()
                        .withDetail("redis", "connected")
                        .build();
            }
            return degraded("unexpected PING response: " + pong);
        } catch (Exception ex) {
            return degraded(ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignored) {
                    // Nothing useful to do while closing a health probe.
                }
            }
        }
    }

    private Health degraded(String reason) {
        log.warn("Redis is unavailable ({}) — rate limiting and caching are degraded. "
                + "The API remains fully available: this is optional infrastructure.", reason);
        return Health.up()
                .withDetail("redis", "unavailable")
                .withDetail("degraded", true)
                .withDetail("impact", "rate limiting falls back to per-instance limits; "
                        + "cache reads go to the database")
                .withDetail("reason", reason)
                .build();
    }
}
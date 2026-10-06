package com.inventory.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the health probe that took the deployed service out
 * of rotation.
 *
 * <p>An instance whose Redis had expired reported {@code {"status":"DOWN"}},
 * which made Render return 503 for every request — including requests that never
 * touch Redis. Redis is optional in this application by design (rate limiter and
 * cache both fail open), so it must not be able to declare the whole API
 * unavailable.
 */
class RedisHealthIndicatorTest {

    @Test
    @DisplayName("a reachable Redis reports UP with no degradation flag")
    void reachableRedisIsUp() throws Exception {
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.ping()).thenReturn("PONG");

        Health health = new RedisHealthIndicator(factoryReturning(connection)).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("connected", health.getDetails().get("redis"));
    }

    @Test
    @DisplayName("an unreachable Redis is reported UP but visibly degraded")
    void unreachableRedisIsDegradedNotDown() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection())
                .thenThrow(new RuntimeException("Connection refused: localhost/127.0.0.1:6379"));

        Health health = new RedisHealthIndicator(factory).health();

        assertEquals(Status.UP, health.getStatus(),
                "an optional dependency must not make the whole API report DOWN");
        assertEquals("unavailable", health.getDetails().get("redis"));
        assertEquals(Boolean.TRUE, health.getDetails().get("degraded"));
        assertNotNull(health.getDetails().get("reason"));
    }

    @Test
    @DisplayName("a Redis that answers PING with something else is treated as degraded")
    void unexpectedPingResponseIsDegraded() throws Exception {
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.ping()).thenReturn("NOPE");

        Health health = new RedisHealthIndicator(factoryReturning(connection)).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(Boolean.TRUE, health.getDetails().get("degraded"));
    }

    @Test
    @DisplayName("a Redis authentication failure is degraded, not fatal")
    void authFailureIsDegraded() throws Exception {
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.ping()).thenThrow(new RuntimeException("NOAUTH Authentication required."));

        Health health = new RedisHealthIndicator(factoryReturning(connection)).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("unavailable", health.getDetails().get("redis"));
    }

    private static RedisConnectionFactory factoryReturning(RedisConnection connection) {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenReturn(connection);
        return factory;
    }
}
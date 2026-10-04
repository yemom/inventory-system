package com.inventory.backend.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Custom health indicators for liveness and readiness probes.
 * <p>
 * Liveness: is the application process running?
 * Readiness: is the application ready to serve traffic?
 * <p>
 * The readiness probe checks database connectivity.
 * If the database is down, the instance is marked as not ready
 * and the load balancer stops sending traffic to it.
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                return Health.up()
                        .withDetail("database", "PostgreSQL")
                        .withDetail("status", "connected")
                        .build();
            }
        } catch (SQLException e) {
            return Health.down()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("status", "unreachable")
                    .withDetail("error", e.getMessage())
                    .build();
        }
        return Health.down()
                .withDetail("database", "PostgreSQL")
                .withDetail("status", "invalid")
                .build();
    }
}

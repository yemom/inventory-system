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

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(DatabaseHealthIndicator.class);

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
            // /actuator/health is served with show-details=never, so the response
            // body is only {"status":"DOWN"} — useless for telling a dead database
            // apart from any other unhealthy component. Name the real cause in the
            // log, where an operator reads it, instead of leaking it over an
            // unauthenticated endpoint.
            log.error("PostgreSQL is unreachable: {}", e.getMessage(), e);
            return Health.down()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("status", "unreachable")
                    .withDetail("error", e.getMessage())
                    .build();
        }
        log.error("PostgreSQL connection obtained but reported invalid (isValid() returned false). "
                + "The instance will be taken out of rotation.");
        return Health.down()
                .withDetail("database", "PostgreSQL")
                .withDetail("status", "invalid")
                .build();
    }
}

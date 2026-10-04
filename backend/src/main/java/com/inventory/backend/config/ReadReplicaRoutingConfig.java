package com.inventory.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional PostgreSQL read-replica routing.
 * <p>
 * <b>Disabled by default.</b> It only activates when {@code READ_REPLICA_URL}
 * is set, so local development and CI run against a single primary with the
 * stock Spring Boot datasource auto-configuration (which honours
 * {@code spring.datasource.url} — important because tests override it).
 * <p>
 * Routing rules:
 * <ul>
 *   <li>Write transaction / no transaction → <b>primary</b></li>
 *   <li>{@code @Transactional(readOnly = true)} → <b>replica</b></li>
 * </ul>
 * Methods that must observe read-after-write consistency (sale creation,
 * stock checks, payments) are intentionally <b>not</b> {@code readOnly}, so
 * they always hit the primary.
 * <p>
 * If the replica is unreachable the routing datasource falls back to the
 * primary, so a replica outage never blocks transactional writes.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "READ_REPLICA_URL")
public class ReadReplicaRoutingConfig {

    @Value("${READ_REPLICA_URL}")
    private String replicaUrl;

    @Value("${READ_REPLICA_USERNAME:${spring.datasource.username:stockflow}}")
    private String replicaUsername;

    @Value("${READ_REPLICA_PASSWORD:${spring.datasource.password:}}")
    private String replicaPassword;

    @Bean
    @Primary
    public DataSource routingDataSource(
            @Qualifier("dataSource") DataSource primary,
            DataSource replica) {

        Map<Object, Object> targets = new LinkedHashMap<>();
        targets.put("primary", primary);
        targets.put("replica", replica);

        RoutingDataSource routing = new RoutingDataSource();
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(primary);
        routing.afterPropertiesSet();

        log.warn("READ_REPLICA_URL is set — read-only transactions will be routed to the replica. "
                + "Verify replication lag is acceptable for your dashboards/reports.");
        return routing;
    }

    /** Replica target. Read-only and smaller, since it only serves SELECT traffic. */
    @Bean
    public DataSource replica(@Value("${DB_POOL_MAX:10}") int poolMax) {
        com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
        ds.setJdbcUrl(replicaUrl);
        ds.setUsername(replicaUsername);
        ds.setPassword(replicaPassword);
        ds.setReadOnly(true);
        ds.setPoolName("StockFlowReplica");
        ds.setMaximumPoolSize(Math.max(1, poolMax / 2));
        ds.setMinimumIdle(1);
        ds.setConnectionTimeout(10000);
        ds.setIdleTimeout(300000);
        ds.setMaxLifetime(900000);
        return ds;
    }

    /**
     * Routes by transaction read-only flag: read-only → replica, otherwise → primary.
     */
    public static class RoutingDataSource extends AbstractRoutingDataSource {
        @Override
        protected Object determineCurrentLookupKey() {
            boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
            return readOnly ? "replica" : "primary";
        }
    }
}
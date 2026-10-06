package com.inventory.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Opens one real connection to PostgreSQL <b>before</b> JPA/Hibernate is
 * initialised, so a database problem is reported as a database problem.
 *
 * <p>Why this exists: when Hibernate cannot read JDBC metadata it fails with
 * {@code "Unable to determine Dialect without JDBC metadata (please set
 * 'jakarta.persistence.jdbc.url' ...)"} — even though a perfectly good URL is
 * configured. That single message covers every possible cause: an unusable URL,
 * wrong credentials, a refused connection, an unreachable host, an expired or
 * asleep free-tier database, a TLS mismatch. The deploy log then contains 120
 * lines of Spring bean-creation noise and zero actionable information.
 *
 * <p>This verifier runs on the {@code DataSource} bean after it is initialised.
 * That is early enough to matter: {@code LocalContainerEntityManagerFactoryBean}
 * receives the DataSource by autowiring, so the DataSource is fully created —
 * including this check — before Hibernate is built. A failure therefore surfaces
 * with the real cause instead of the misleading dialect error.
 *
 * <p>It deliberately does <b>not</b> implement its own retry loop. HikariCP
 * already retries for {@code initialization-fail-timeout} (60s, see
 * {@code application.properties}) while filling the pool, so a single
 * {@code getConnection()} already absorbs a database that is still starting up.
 * Once that window is exhausted the database really is unreachable and failing
 * fast is the correct behaviour.
 *
 * <p>Credentials are never logged: the URL is sanitised first.
 */
@Component
public class DataSourceReadinessVerifier implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DataSourceReadinessVerifier.class);

    private final Environment environment;

    public DataSourceReadinessVerifier(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource dataSource) {
            verify(dataSource);
        }
        return bean;
    }

    void verify(DataSource dataSource) {
        String url = sanitizeJdbcUrl(environment.getProperty("spring.datasource.url"));

        try (Connection ignored = dataSource.getConnection()) {
            log.info("PostgreSQL connection established via {}", url);
        } catch (SQLException | RuntimeException ex) {
            // RuntimeException matters: when the driver simply refuses the URL,
            // HikariCP's DriverDataSource throws
            //   RuntimeException: Driver org.postgresql.Driver claims to not
            //   accept jdbcUrl, postgresql://…
            // *before* any SQLException exists. Catching only SQLException let
            // that raw driver message escape — which is precisely the case an
            // operator needs explained.
            Throwable root = rootCause(ex);
            throw new IllegalStateException(buildFailureMessage(url, root), root);
        }
    }

    private static String buildFailureMessage(String url, Throwable root) {
        String reason = root.getMessage() == null || root.getMessage().isBlank()
                ? root.getClass().getName()
                : root.getClass().getSimpleName() + ": " + root.getMessage();

        return """
                Unable to connect to PostgreSQL at %s.

                Cause: %s

                spring.datasource.url IS configured, so Hibernate's
                "Unable to determine Dialect without JDBC metadata" is a
                misleading symptom of this connection failure, not a missing
                setting. Check, in order:
                  1. Is the database running? Managed free-tier databases sleep
                     and expire; a suspended database refuses connections.
                  2. Are the host/port the INTERNAL connection details? The
                     public hostname is not reachable from inside the platform.
                  3. Do DATABASE_USERNAME / DATABASE_PASSWORD match the database?
                  4. Does the database's IP allow list permit this service?
                  5. Does TLS need to be enabled (sslmode=require)?
                """.formatted(url, reason);
    }

    /**
     * Replaces any {@code user:password@} component with {@code ***} so the URL
     * can be logged. Handles both JDBC and URI-shaped values.
     */
    static String sanitizeJdbcUrl(String url) {
        if (url == null || url.isBlank()) {
            return "(not configured)";
        }
        String value = url.trim();

        int authorityStart = value.indexOf("://");
        if (authorityStart < 0) {
            return value;
        }
        authorityStart += 3;

        int authorityEnd = value.indexOf('/', authorityStart);
        if (authorityEnd < 0) {
            authorityEnd = value.length();
        }

        String authority = value.substring(authorityStart, authorityEnd);
        int at = authority.lastIndexOf('@');
        if (at < 0) {
            return value;
        }
        return value.substring(0, authorityStart)
                + "***@"
                + authority.substring(at + 1)
                + value.substring(authorityEnd);
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
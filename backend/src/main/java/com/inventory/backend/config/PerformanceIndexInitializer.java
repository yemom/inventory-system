package com.inventory.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Applies the performance indexes in {@code db/performance-indexes.sql}
 * after the application is ready.
 * <p>
 * <b>Why not Flyway:</b> this project's schema is owned by Hibernate
 * ({@code spring.jpa.hibernate.ddl-auto=update}). Flyway runs <i>before</i>
 * Hibernate, so an index migration would reference tables that do not exist
 * yet. Worse, {@code baseline-on-migrate} marks a fresh/empty database as
 * "baseline v1" and skips all migrations, which silently leaves the database
 * without its schema. Applying indexes here, after Hibernate has created the
 * tables, avoids both problems.
 * <p>
 * Every statement is idempotent ({@code CREATE INDEX IF NOT EXISTS}) and each
 * is executed independently, so a failure on one index cannot prevent startup
 * and can simply be fixed later.
 * <p>
 * Disable with {@code app.performance-indexes.enabled=false} if indexes are
 * managed externally (for example by a DBA-provided migration pipeline).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(0)
public class PerformanceIndexInitializer {

    private static final String SCRIPT = "db/performance-indexes.sql";

    private final DataSource dataSource;

    @Value("${app.performance-indexes.enabled:true}")
    private boolean enabled;

    @EventListener(ApplicationReadyEvent.class)
    public void applyIndexes() {
        if (!enabled) {
            log.info("Performance indexes are disabled (app.performance-indexes.enabled=false)");
            return;
        }

        List<String> statements = readStatements();
        if (statements.isEmpty()) {
            return;
        }

        int applied = 0;
        int skipped = 0;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            for (String sql : statements) {
                try {
                    statement.execute(sql);
                    applied++;
                } catch (Exception e) {
                    // Never fail startup because of a single index: log and continue.
                    skipped++;
                    log.warn("Could not apply index: {} — {}", sql, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Could not connect to the database to apply performance indexes: {}", e.getMessage());
            return;
        }

        log.info("Performance indexes applied ({} created, {} skipped)", applied, skipped);
    }

    private List<String> readStatements() {
        try {
            String content;
            try (var in = new ClassPathResource(SCRIPT).getInputStream()) {
                content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            return Arrays.stream(content.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    // strip comment-only lines
                    .filter(s -> !s.startsWith("--"))
                    .toList();
        } catch (Exception e) {
            log.warn("Performance index script {} not found: {}", SCRIPT, e.getMessage());
            return List.of();
        }
    }
}
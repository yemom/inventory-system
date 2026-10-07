package com.inventory.backend.config;

import com.inventory.backend.model.StockMovement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fills in {@code stock_movements.status} for rows written before the column
 * existed.
 *
 * <p>This exists because of how the column was added. {@code StockMovement} grew
 * a {@code status} column when the approval workflow was introduced, and
 * {@code spring.jpa.hibernate.ddl-auto=update} is the only thing applying schema
 * changes. Hibernate emits
 *
 * <pre>ALTER TABLE stock_movements ADD COLUMN status varchar(16) NOT NULL</pre>
 *
 * and on a table that already holds rows PostgreSQL refuses it:
 * {@code ERROR: column "status" of relation "stock_movements" contains null
 * values}. Hibernate treats that as a warning, not a fatal error, so the
 * application starts, passes its health check and looks perfectly healthy — while
 * the column was never created. Every endpoint that reads or writes
 * {@code status} then fails at request time with a 500.
 *
 * <p>The column is now declared nullable so the ALTER succeeds. That leaves the
 * pre-existing rows with {@code NULL}, which is not a meaningful state: those
 * movements were written before approvals existed and were applied to stock the
 * moment they were recorded. So they are {@code APPROVED}, and this fills them
 * in.
 *
 * <p>Idempotent, and a no-op on a database that was created after the column
 * arrived — which is why every test passes without it doing anything.
 */
@Component
public class StockMovementStatusBackfill {

    private static final Logger log = LoggerFactory.getLogger(StockMovementStatusBackfill.class);

    private final JdbcTemplate jdbcTemplate;

    public StockMovementStatusBackfill(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Runs after the context is ready, so {@code ddl-auto=update} has already
     * added the column.
     *
     * <p>{@link ApplicationReadyEvent} rather than {@code @PostConstruct}: schema
     * update happens while the SessionFactory is built, so at construction time
     * the column may not exist yet.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void backfillLegacyStatus() {
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE stock_movements SET status = ? WHERE status IS NULL",
                    StockMovement.Status.APPROVED);

            if (updated > 0) {
                log.info("Backfilled {} pre-existing stock movement(s) to status=APPROVED. "
                        + "They predate the approval workflow and were applied to stock "
                        + "on the spot.", updated);
            }
        } catch (Exception e) {
            // Must not stop the application from serving traffic. If the column
            // genuinely is missing, ddl-auto=update will log the real cause; a
            // backfill failing on top of that would only bury it.
            log.warn("Could not backfill stock_movements.status: {}", e.getMessage());
        }
    }
}

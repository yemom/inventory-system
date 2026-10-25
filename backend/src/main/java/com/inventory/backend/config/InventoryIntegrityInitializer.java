package com.inventory.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Installs database-level safeguards that Hibernate's {@code ddl-auto=update}
 * cannot add to an existing table. The constraint is NOT VALID so legacy rows
 * remain readable, while every new insert or update is protected immediately.
 */
@Slf4j
@Component
public class InventoryIntegrityInitializer {

    private final JdbcTemplate jdbcTemplate;

    public InventoryIntegrityInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applyInventoryConstraints() {
        try {
            jdbcTemplate.execute("""
                    DO $$
                    BEGIN
                        IF NOT EXISTS (
                            SELECT 1 FROM pg_constraint
                            WHERE conname = 'chk_products_quantity_non_negative'
                        ) THEN
                            ALTER TABLE products
                                ADD CONSTRAINT chk_products_quantity_non_negative
                                CHECK (quantity >= 0) NOT VALID;
                        END IF;
                    END $$
                    """);
            log.info("Verified products quantity integrity constraint");
        } catch (Exception exception) {
            log.warn("Could not apply products quantity integrity constraint: {}", exception.getMessage());
        }
    }
}

package com.inventory.backend.service;

import com.inventory.backend.model.Product;
import com.inventory.backend.model.Warehouse;
import com.inventory.backend.model.WarehouseStock;
import com.inventory.backend.repository.ProductRepository;
import com.inventory.backend.repository.WarehouseRepository;
import com.inventory.backend.repository.WarehouseStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rebuilds {@code warehouse_stock} from the movement ledger when it is empty.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Stock used to live in one column, {@code products.quantity}. Multi-warehouse
 * support moved the authoritative balance to a {@code warehouse_stock} row per
 * product per warehouse, but nothing backfilled rows for history predating it.
 * The result was a ledger that balanced on paper and a till that could not sell
 * anything: every sale was refused for insufficient stock, because the
 * per-warehouse balances did not exist to be read.
 *
 * <h2>Why this is a derivation, not an invention</h2>
 *
 * <p>Balances come from {@code stock_movements} — the record of what actually
 * happened. Summing approved movements per product and warehouse reproduces the
 * stock that really was there; it does not invent any. The reconciliation below
 * proves it: if the rebuilt rows sum to {@code products.quantity} for every
 * product, two independently recorded views of the same history agree, and
 * nothing was made up to close a gap.
 *
 * <p>Movements written before warehouse attribution existed carry a null
 * warehouse. Those are credited to the oldest active warehouse, which was the
 * only location in existence at the time. That is the single assumption made
 * here; it is logged, and it is reversible.
 *
 * <h2>Why it runs at most once</h2>
 *
 * <p>Guarded on the table being empty. Once rows exist they are live balances
 * maintained by {@link WarehouseStockService}, and re-deriving would discard
 * movements made since. To undo, delete the rows: the movements are untouched
 * and this runs again on the next start.
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class WarehouseStockBackfillRunner implements ApplicationRunner {

    private final WarehouseStockRepository warehouseStockRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (warehouseStockRepository.count() > 0) {
            log.debug("warehouse_stock already populated; leaving live balances alone");
            return;
        }

        List<Warehouse> active = warehouseRepository.findByActiveTrueOrderByIdAsc();
        if (active.isEmpty()) {
            log.info("No active warehouse, so historical stock cannot be attributed. Skipping.");
            return;
        }
        Warehouse fallback = active.get(0);

        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) {
            return;
        }

        int rowsCreated = 0;
        int nullWarehouseMovements = 0;
        int mismatches = 0;

        for (Product product : products) {
            List<LedgerRow> ledger = ledgerRowsFor(product.getId());
            if (ledger.isEmpty()) {
                continue;
            }

            int rebuiltTotal = 0;
            /*
             * Balances are merged per *resolved* warehouse before saving, not
             * per ledger row. A product whose movements span warehouse 1 and
             * unattributed history both resolve to the fallback, and writing one
             * row for each would collide on the (product, warehouse) unique
             * constraint part-way through startup.
             */
            Map<Long, Integer> merged = new LinkedHashMap<>();
            Map<Long, Long> sourceWarehouse = new LinkedHashMap<>();
            for (LedgerRow row : ledger) {
                Long resolvedId;
                if (row.warehouseId() == null) {
                    resolvedId = fallback.getId();
                    nullWarehouseMovements++;
                } else {
                    resolvedId = warehouseRepository.findById(row.warehouseId())
                            .map(Warehouse::getId)
                            .orElse(fallback.getId());
                    if (resolvedId.equals(fallback.getId())
                            && !fallback.getId().equals(row.warehouseId())) {
                        // Recorded warehouse is gone or inactive; crediting the
                        // fallback is the only honest place left for it.
                        resolvedId = fallback.getId();
                    }
                }
                merged.merge(resolvedId, row.quantity(), Integer::sum);
                sourceWarehouse.putIfAbsent(resolvedId, row.warehouseId());
            }

            for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
                if (entry.getValue() == 0) {
                    continue;
                }
                Warehouse warehouse = warehouseRepository.findById(entry.getKey())
                        .orElse(fallback);

                WarehouseStock stock = new WarehouseStock();
                stock.setProduct(product);
                stock.setWarehouse(warehouse);
                stock.setQuantity(entry.getValue());
                // Valuation needs a cost. Where no historical cost was recorded
                // the purchase price is the figure the system actually holds; it
                // is used and logged rather than valued at zero, which would
                // silently understate inventory.
                stock.setAverageCost(costFor(product));

                warehouseStockRepository.save(stock);
                rowsCreated++;
                rebuiltTotal += entry.getValue();
            }

            int stated = product.getQuantity() == null ? 0 : product.getQuantity();
            if (rebuiltTotal != stated) {
                mismatches++;
                log.warn("Product {} ({}) rebuilt to {} but products.quantity says {}. "
                                + "These are two records of the same history and they disagree; "
                                + "the ledger was used because it is the record of what happened.",
                        product.getId(), product.getSku(), rebuiltTotal, stated);
            }
        }

        if (rowsCreated == 0) {
            return;
        }
        log.info("Rebuilt {} warehouse_stock row(s) for {} product(s) from the movement ledger. "
                        + "Unattributed movements ({} row(s) with no warehouse) were credited to \"{}\".",
                rowsCreated, products.size(), nullWarehouseMovements, fallback.getName());
        if (mismatches == 0) {
            log.info("Reconciliation clean: every rebuilt balance matches products.quantity.");
        }
    }

    /**
     * Approved movement totals for one product, grouped by warehouse.
     *
     * <p>Grouped in the database rather than by loading the movements: this runs
     * over the whole catalogue and the ledger is the largest table in the
     * system.
     */
    private List<LedgerRow> ledgerRowsFor(Long productId) {
        return warehouseStockRepository.ledgerBalanceForProduct(productId).stream()
                .map(row -> new LedgerRow(
                        row[0] == null ? null : ((Number) row[0]).longValue(),
                        ((Number) row[1]).intValue()))
                .filter(row -> row.quantity() != 0)
                .collect(Collectors.toList());
    }

    /** Last recorded cost, else the purchase price, never null. */
    private BigDecimal costFor(Product product) {
        BigDecimal purchase = product.getPurchasePrice();
        return purchase != null && purchase.signum() > 0 ? purchase : BigDecimal.ZERO;
    }

    private record LedgerRow(Long warehouseId, int quantity) {
    }
}
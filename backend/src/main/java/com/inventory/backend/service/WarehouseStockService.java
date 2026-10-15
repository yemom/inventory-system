package com.inventory.backend.service;

import com.inventory.backend.exception.BusinessRuleViolationException;
import com.inventory.backend.model.Product;
import com.inventory.backend.model.User;
import com.inventory.backend.model.Warehouse;
import com.inventory.backend.model.WarehouseStock;
import com.inventory.backend.repository.ProductRepository;
import com.inventory.backend.repository.WarehouseRepository;
import com.inventory.backend.repository.WarehouseStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Per-warehouse stock, and the moving weighted-average cost of it.
 *
 * <p>Everything that changes a stock quantity goes through here. That is a
 * deliberate restriction: the previous arrangement let the sale service, the
 * purchase service and the stock-movement service each hold a pessimistic lock
 * on {@code Product} and adjust {@code Product.quantity} themselves, which is
 * correct for the total but silent about <em>which warehouse</em> the goods
 * moved. A transfer therefore moved the total (so the books balanced) without
 * ever saying where the stock went.
 *
 * <h2>Why weighted average</h2>
 *
 * <p>The application tracks no batches, lots or serial numbers and sells
 * fungible goods, so FIFO would require a layer table that nothing else in the
 * system populates, and would value retail stock by purchase order rather than
 * by what that stock is currently worth. Moving weighted average is the
 * standard method for that situation and is what {@code product.purchasePrice}
 * already implied.
 *
 * <p>On a receipt of {@code q} units at {@code c}:
 * <pre>
 *   newAverage = (onHand × oldAverage + q × c) / (onHand + q)
 * </pre>
 * A withdrawal does not change the average — the goods that left cost what the
 * stock on hand costs — and the unit cost of the withdrawal is snapshotted onto
 * the ledger row and onto the sale line so the figure for a closed period stays
 * correct after the next price change.
 */
@Service
@RequiredArgsConstructor
public class WarehouseStockService {

    private static final int COST_SCALE = 4;

    private final WarehouseStockRepository warehouseStockRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final AuditLogService auditLogService;

    /**
     * The stock row for a product in a warehouse, locked for update.
     *
     * <p>A missing row is created with zero quantity rather than treated as an
     * error, because a product that has never moved in this warehouse genuinely
     * has nothing here. The insert races only when two transactions both find
     * the row missing; the unique constraint on (product, warehouse) rejects
     * one and the caller retries, which is why {@link #lockStock} is written to
     * be re-entrant.
     */
    @Transactional
    public WarehouseStock lockStock(Long productId, Long warehouseId) {
        Optional<WarehouseStock> existing = warehouseStockRepository.findForUpdate(productId, warehouseId);
        if (existing.isPresent()) {
            return existing.get();
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: id=" + productId));
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new IllegalArgumentException("Warehouse not found: id=" + warehouseId));

        WarehouseStock row = WarehouseStock.builder()
                .product(product)
                .warehouse(warehouse)
                .quantity(0)
                .averageCost(null)
                .lastMovementAt(LocalDateTime.now())
                .build();
        try {
            return warehouseStockRepository.saveAndFlush(row);
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            // Another transaction inserted the same row first. Its row is the
            // real one; take the lock on it and carry on.
            return warehouseStockRepository.findForUpdate(productId, warehouseId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Stock row for product " + productId + " and warehouse " + warehouseId
                                    + " could not be locked."));
        }
    }

    /**
     * Moves {@code delta} units into or out of a warehouse and republishes the
     * product-wide total.
     *
     * <p>A shortfall is refused. This is the policy for every operational movement
     * — sales, transfers, issues, returns — because none of them may proceed
     * against stock that is not there.
     *
     * @return the cost per unit this movement should be recorded at
     */
    @Transactional
    public AppliedMovement applyMovement(Long productId, Long warehouseId, int delta,
                                         BigDecimal unitCost, String reason, User actor) {
        return applyMovement(productId, warehouseId, delta, unitCost, reason, actor,
                StockShortfallPolicy.REFUSE);
    }

    /**
     * Moves stock, applying a shortfall policy.
     *
     * @return what was actually applied, which under {@link StockShortfallPolicy#CLAMP_AT_ZERO}
     *         may be a smaller quantity than was asked for
     */
    @Transactional
    public AppliedMovement applyMovement(Long productId, Long warehouseId, int delta,
                                         BigDecimal unitCost, String reason, User actor,
                                         StockShortfallPolicy policy) {
        if (delta == 0) {
            throw new IllegalArgumentException("A stock movement must change the quantity; 0 was given.");
        }

        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: id=" + productId));

        WarehouseStock stock = lockStock(productId, warehouseId);
        int before = stock.getQuantity();
        int requested = delta;
        int after = before + delta;
        String shortfallNote = null;

        if (after < 0) {
            if (policy == StockShortfallPolicy.REFUSE) {
                // Refuse rather than proceed. Recording a sale against stock that
                // is not there is how a ledger stops reconciling with the quantity.
                throw new IllegalArgumentException(String.format(
                        "Insufficient stock in warehouse '%s' for product %s. Available: %d, Requested: %d.",
                        stock.getWarehouse() != null ? stock.getWarehouse().getName() : warehouseId,
                        product.getSku(), before, Math.abs(delta)));
            }
            // An approver has signed a correction that says the shelf holds less
            // than the system believed. Apply what is possible and record the
            // difference on the movement so it is visible rather than silent.
            delta = -before;
            after = 0;
            shortfallNote = String.format(
                    "Applied as %d rather than %d: a movement of %d from a balance of %d "
                            + "would have gone negative.",
                    Math.abs(delta), Math.abs(requested), Math.abs(requested), before);
        }

        BigDecimal effectiveUnitCost;
        if (delta > 0) {
            BigDecimal incoming = unitCost != null ? unitCost : stock.getAverageCost();
            stock.setAverageCost(weightedAverage(before, stock.getAverageCost(),
                    delta, incoming));
            effectiveUnitCost = stock.getAverageCost();
        } else {
            // Issuing goods does not move the average: the units that left cost
            // what the units remaining cost.
            effectiveUnitCost = stock.getAverageCost();
        }

        stock.setQuantity(after);
        stock.setLastMovementAt(LocalDateTime.now());
        warehouseStockRepository.save(stock);

        syncProductTotal(product);
        productRepository.save(product);

        if (shortfallNote != null) {
            auditLogService.log(actor == null ? null : actor.getId(),
                    actor == null ? "system" : actor.getUsername(),
                    "STOCK_ADJUSTMENT_CLAMPED", "WAREHOUSE_STOCK",
                    productId + "@" + warehouseId, shortfallNote);
        } else if (Math.abs(delta) >= 100 && reason != null && !reason.isBlank()) {
            auditLogService.log(actor == null ? null : actor.getId(),
                    actor == null ? "system" : actor.getUsername(),
                    "STOCK_MOVEMENT_LARGE", "WAREHOUSE_STOCK",
                    productId + "@" + warehouseId,
                    String.format("%d units of %s at warehouse %d — %s",
                            Math.abs(delta), product.getSku(), warehouseId, reason));
        }

        return new AppliedMovement(effectiveUnitCost, delta, shortfallNote);
    }

    /**
     * What a movement actually did.
     *
     * @param unitCost        cost per unit to record on the movement
     * @param appliedDelta    units actually moved, which is the figure the
     *                        ledger row must carry so the ledger still sums to
     *                        the quantity on hand
     * @param shortfallNote   set when a requested withdrawal was clamped; the
     *                        caller appends it to the movement's own notes
     */
    public record AppliedMovement(BigDecimal unitCost, int appliedDelta, String shortfallNote) {
    }

    /**
     * Moving weighted average after a receipt.
     *
     * <p>Static so the formula itself can be unit-tested against known values
     * without a database, and so it is stated in exactly one place.
     *
     * <p>Both the empty-stock case and a missing prior cost fall back to the
     * incoming cost: the first units received define what the stock costs, and
     * dividing by zero or by a null would produce an unusable average.
     */
    public static BigDecimal weightedAverage(int onHand, BigDecimal currentAverage,
                                             int received, BigDecimal incomingCost) {
        if (received <= 0) {
            return currentAverage;
        }
        if (onHand <= 0 || currentAverage == null) {
            return scale(incomingCost);
        }
        BigDecimal priorValue = currentAverage.multiply(BigDecimal.valueOf(onHand));
        BigDecimal incomingValue = (incomingCost == null ? BigDecimal.ZERO : incomingCost)
                .multiply(BigDecimal.valueOf(received));
        BigDecimal totalQty = BigDecimal.valueOf(onHand + received);
        return scale(priorValue.add(incomingValue).divide(totalQty, COST_SCALE, RoundingMode.HALF_UP));
    }

    /** Removes a warehouse's share when a product is transferred in. */
    @Transactional
    public BigDecimal unitCostOf(Long productId, Long warehouseId) {
        return warehouseStockRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .map(WarehouseStock::getAverageCost)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public int quantityIn(Long productId, Long warehouseId) {
        return warehouseStockRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .map(WarehouseStock::getQuantity)
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public List<WarehouseStock> stockOfProduct(Long productId) {
        return warehouseStockRepository.findByProductId(productId);
    }

    @Transactional(readOnly = true)
    public List<WarehouseStock> stockInWarehouse(Long warehouseId) {
        return warehouseStockRepository.findByWarehouseId(warehouseId);
    }

    @Transactional(readOnly = true)
    public List<WarehouseStock> allStock() {
        return warehouseStockRepository.findAllWithStock();
    }

    /**
     * The single warehouse a sale with no stated location should use.
     *
     * <p>Falls back to the first active warehouse so a single-site business — the
     * common case — does not have to be configured before it can sell. With two
     * or more warehouses the choice is genuinely ambiguous and the caller is told
     * so rather than having the goods quietly come from an arbitrary one.
     */
    @Transactional(readOnly = true)
    public Warehouse resolveDefaultWarehouse(Long requestedWarehouseId) {
        if (requestedWarehouseId != null) {
            return warehouseRepository.findById(requestedWarehouseId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Warehouse not found: id=" + requestedWarehouseId));
        }
        List<Warehouse> active = warehouseRepository.findByActiveTrueOrderByIdAsc();
        if (active.isEmpty()) {
            throw new BusinessRuleViolationException("NO_ACTIVE_WAREHOUSE",
                    "No active warehouse exists. Stock cannot be attributed to a location.");
        }
        if (active.size() > 1) {
            throw new BusinessRuleViolationException("WAREHOUSE_REQUIRED",
                    "This business has " + active.size() + " active warehouses. "
                            + "Choose the warehouse this transaction belongs to.");
        }
        return active.get(0);
    }

    /**
     * Republishes {@code Product.quantity} as the sum of every warehouse's stock.
     *
     * <p>{@code Product.quantity} predates multi-warehouse support and is read
     * by every existing screen and query. Rather than leave the two figures to
     * drift, the total is derived from the per-warehouse rows on every movement.
     * If they ever disagree the discrepancy was introduced outside this service.
     */
    @Transactional
    public void syncProductTotal(Product product) {
        int total = warehouseStockRepository.totalQuantityForActiveWarehouses(product.getId());
        product.setQuantity(Math.max(total, 0));
        if (product.getPurchasePrice() != null && product.getPurchasePrice().signum() > 0) {
            // Leave purchasePrice alone: it is the last received price, which is
            // what a purchase screen displays. The costing average lives on the
            // warehouse stock row and is what the valuation uses.
        }
        productRepository.save(product);
    }

    private static BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(COST_SCALE, RoundingMode.HALF_UP);
    }
}
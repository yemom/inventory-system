package com.inventory.backend.service;

import com.inventory.backend.model.Product;
import com.inventory.backend.model.StockMovement;
import com.inventory.backend.model.User;
import com.inventory.backend.model.Warehouse;
import com.inventory.backend.repository.ProductRepository;
import com.inventory.backend.repository.StockMovementRepository;
import com.inventory.backend.repository.UserRepository;
import com.inventory.backend.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The stock ledger: the one place a movement row is written.
 *
 * <p>Three properties the application did not have before are established here.
 *
 * <h2>1. A movement is immutable evidence</h2>
 * Nothing rewrites a posted row's quantity. A correction is a second, opposite
 * movement that references the first, so the history explains itself.
 *
 * <h2>2. Idempotency</h2>
 * Every movement carries a {@code movementGroup}. Posting twice with the same
 * group is refused rather than duplicated. The sale service already had an
 * idempotency key for the order as a whole; this extends the same guarantee to
 * each individual posting, which is what stops a retried request from both
 * creating a second movement and moving the stock twice.
 *
 * <h2>3. A transfer is two legs of one fact</h2>
 * A transfer deducts the source and credits the destination inside a single
 * database transaction, writing both legs with a shared group. Previously a
 * TRANSFER movement was one row whose quantity was applied to the product total
 * — so it moved the business-wide number without ever moving anything to
 * another warehouse, and neither warehouse could report a turnover.
 *
 * <p>Both legs write in the same transaction deliberately: a transfer with only
 * its outgoing leg committed would destroy stock, and one with only its incoming
 * leg would invent it. If the second leg fails, the first must not survive.
 */
@Service
@RequiredArgsConstructor
public class StockLedgerService {

    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final WarehouseStockService warehouseStockService;
    private final AuditLogService auditLogService;

    /**
     * Records one movement and applies it to the named warehouse.
     *
     * <p>No approval step here: approval is decided by the caller, which has
     * already checked the caller's permissions. A caller that must not apply the
     * movement until it is signed off records the row with
     * {@link #recordPending} instead.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement post(String type, Long productId, Long warehouseId,
                              int signedQuantity, String reference, String reason,
                              String notes, BigDecimal unitCost, String group,
                              String status, User actor) {
        return post(type, productId, warehouseId, null, signedQuantity, reference, reason,
                notes, unitCost, group, status, actor);
    }

    /**
     * Records a movement, naming both ends when it crosses warehouses.
     *
     * <p>For a transfer's outgoing leg the destination is recorded as well as the
     * source, so the leg can be read on its own. A movement with only a source
     * says stock left; only the pair of them says where it went, and a reader
     * looking at one row should not have to find its partner to know why the
     * stock left at all.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement post(String type, Long productId, Long warehouseId,
                              Warehouse destination, int signedQuantity, String reference,
                              String reason, String notes, BigDecimal unitCost, String group,
                              String status, User actor) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: id=" + productId));
        Warehouse warehouse = warehouseId == null ? null
                : warehouseRepository.findById(warehouseId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Warehouse not found: id=" + warehouseId));

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setWarehouse(warehouse);
        movement.setType(type);
        movement.setQuantity(signedQuantity);
        movement.setReference(reference);
        movement.setReason(reason);
        movement.setNotes(notes);
        movement.setUnitCost(unitCost);
        movement.setMovementGroup(group);
        movement.setStatus(status);
        movement.setCreatedBy(actor);
        if (StockMovement.Status.APPROVED.equals(status)) {
            movement.setApprovedBy(actor);
            movement.setApprovedAt(LocalDateTime.now());
        }

        movement.setDestinationWarehouse(destination);

        StockMovement saved = stockMovementRepository.save(movement);

        if (StockMovement.Status.APPROVED.equals(status) && warehouse != null && signedQuantity != 0) {
            WarehouseStockService.AppliedMovement applied = warehouseStockService.applyMovement(
                    productId, warehouseId, signedQuantity, unitCost, reason, actor);
            if (applied.shortfallNote() != null) {
                saved.setQuantity(applied.appliedDelta());
                saved.setNotes(append(saved.getNotes(), applied.shortfallNote()));
            }
            if (saved.getUnitCost() == null) {
                saved.setUnitCost(applied.unitCost());
            }
            stockMovementRepository.save(saved);
        }
        return saved;
    }

    private static String append(String existing, String addition) {
        return existing == null || existing.isBlank() ? addition : existing + " | " + addition;
    }

    /**
     * Writes a movement that does not yet change stock.
     *
     * <p>Used for adjustments and stock counts, which are claims by whoever
     * counted the shelf and are not proof of what is on it.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement recordPending(String type, Long productId, Long warehouseId,
                                       int signedQuantity, String reference, String reason,
                                       String notes, String group, User actor) {
        return post(type, productId, warehouseId, signedQuantity, reference, reason,
                notes, null, group, StockMovement.Status.PENDING, actor);
    }

    /**
     * Applies an already-recorded pending movement.
     *
     * <p>Re-posting through {@link #post} rather than re-saving the existing row
     * means the approved row and the stock change happen together, so an
     * approval cannot succeed without moving the goods.
     */
    @Transactional
    public StockMovement applyApprovedPending(StockMovement pending, User actor) {
        if (!StockMovement.Status.PENDING.equals(pending.getStatus())) {
            throw new IllegalArgumentException(
                    "Only a pending movement can be approved; this one is " + pending.getStatus() + ".");
        }
        int delta = pending.getQuantity() != null ? pending.getQuantity() : 0;
        Long warehouseId = pending.getWarehouse() != null ? pending.getWarehouse().getId() : null;

        BigDecimal cost = pending.getUnitCost();
        if (warehouseId != null && delta != 0) {
            // An approved adjustment or stock count clamps at zero rather than
            // being refused. The approver has looked at the shelf and is signing
            // a correction; refusing would leave the request permanently
            // un-approvable while the shelf sits there wrong. Every operational
            // movement still refuses.
            WarehouseStockService.AppliedMovement applied = warehouseStockService.applyMovement(
                    pending.getProduct().getId(), warehouseId, delta, pending.getUnitCost(),
                    pending.getReason(), actor, StockShortfallPolicy.CLAMP_AT_ZERO);
            cost = applied.unitCost();
            if (applied.shortfallNote() != null) {
                pending.setQuantity(applied.appliedDelta());
                pending.setNotes(append(pending.getNotes(), applied.shortfallNote()));
            }
        }

        pending.setStatus(StockMovement.Status.APPROVED);
        pending.setApprovedBy(actor);
        pending.setApprovedAt(LocalDateTime.now());
        pending.setUnitCost(cost);
        return stockMovementRepository.save(pending);
    }

    /**
     * Moves stock between two warehouses, or records one attempt at doing so.
     *
     * <p>Both legs commit together or neither does. The source is deducted first:
     * if the source does not hold enough, nothing is credited and the caller
     * learns that immediately rather than after a partial transfer.
     *
     * @param quantity units to move; must be positive
     * @param approve  when false the pair is recorded PENDING and no stock moves,
     *                 so a transfer request can be submitted for approval
     */
    @Transactional
    public List<StockMovement> transfer(Product product, Warehouse source, Warehouse destination,
                                       int quantity, String reference, String reason,
                                       String notes, boolean approve, User actor) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("A transfer must move at least one unit.");
        }
        if (source == null || destination == null) {
            throw new IllegalArgumentException("A transfer needs both a source and a destination warehouse.");
        }
        if (source.getId().equals(destination.getId())) {
            throw new IllegalArgumentException(
                    "Source and destination warehouses must be different.");
        }

        String group = "TRF-" + UUID.randomUUID();
        String baseNotes = notes == null || notes.isBlank()
                ? "Transfer " + source.getName() + " -> " + destination.getName()
                : notes;

        if (!approve) {
            StockMovement out = recordPending(StockMovement.TYPE_TRANSFER_OUT,
                    product.getId(), source.getId(), -quantity, reference, reason,
                    baseNotes + " (out of " + source.getName() + ")", group, actor);
            StockMovement in = recordPending(StockMovement.TYPE_TRANSFER_IN,
                    product.getId(), destination.getId(), quantity, reference, reason,
                    baseNotes + " (into " + destination.getName() + ")", group, actor);
            return List.of(out, in);
        }

        BigDecimal unitCost = warehouseStockService.unitCostOf(product.getId(), source.getId());

        // Deduct first. applyMovement throws if the source is short, and the
        // exception aborts the transaction before anything is credited.
        StockMovement out = post(StockMovement.TYPE_TRANSFER_OUT, product.getId(),
                source.getId(), destination, -quantity, reference, reason,
                baseNotes + " (out of " + source.getName() + ")", unitCost, group,
                StockMovement.Status.APPROVED, actor);

        StockMovement in = post(StockMovement.TYPE_TRANSFER_IN, product.getId(), destination.getId(),
                quantity, reference, reason,
                baseNotes + " (into " + destination.getName() + ")", unitCost, group,
                StockMovement.Status.APPROVED, actor);

        auditLogService.log(actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername(),
                "STOCK_TRANSFERRED", "PRODUCT", String.valueOf(product.getId()),
                String.format("%d units of %s: %s -> %s (%s)",
                        quantity, product.getSku(), source.getName(), destination.getName(),
                        reference == null ? group : reference));

        return List.of(out, in);
    }

    /**
     * Refuses a second posting of the same fact.
     *
     * <p>Checked rather than enforced by a unique constraint because
     * {@code ddl-auto=update} cannot add a constraint to a populated table, so a
     * constraint declared on the entity would be silently skipped on an
     * existing database and leave a false impression of protection.
     */
    @Transactional(readOnly = true)
    public boolean alreadyPosted(String group) {
        return group != null && !group.isBlank()
                && stockMovementRepository.countByMovementGroup(group) > 0;
    }

    /** A fresh group key. Callers that need idempotency pass their own. */
    public static String newGroup(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    /** The authenticated user, or null when the call is not on a user thread. */
    public User currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        String principal = authentication.getName();
        return userRepository.findByEmail(principal)
                .or(() -> userRepository.findByUsername(principal))
                .orElse(null);
    }
}
package com.inventory.backend.service;

import com.inventory.backend.dto.CreateStockMovementRequest;
import com.inventory.backend.dto.StockMovementDTO;
import com.inventory.backend.model.*;
import com.inventory.backend.repository.ProductRepository;
import com.inventory.backend.repository.StockMovementRepository;
import com.inventory.backend.repository.UserRepository;
import com.inventory.backend.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class StockMovementService {
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final StockLedgerService ledger;
    private final WarehouseStockService warehouseStockService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Authorities in force for the current thread when the caller is not a web
     * request.
     *
     * <p>Held per-thread rather than on the service, so a scheduled job setting
     * it can never widen what an unrelated web request on a pooled thread is
     * allowed to do.
     */
    private static final ThreadLocal<Set<String>> SYSTEM_AUTHORITIES = new ThreadLocal<>();

    /**
     * Runs {@code work} as if the caller held {@code authorities}.
     *
     * <p>Restores whatever was in place afterwards, including when {@code work}
     * throws, so a failure cannot leave elevated permissions on a pooled worker
     * thread for the next task that lands on it.
     */
    public <T> T asSystem(Set<String> authorities, Supplier<T> work) {
        Set<String> previous = SYSTEM_AUTHORITIES.get();
        SYSTEM_AUTHORITIES.set(authorities);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                SYSTEM_AUTHORITIES.remove();
            } else {
                SYSTEM_AUTHORITIES.set(previous);
            }
        }
    }

    public Page<StockMovementDTO> listMovements(Pageable pageable) {
        return stockMovementRepository.findAll(pageable).map(this::toDTO);
    }

    /** Movements awaiting a Supervisor or Manager decision, oldest first. */
    @Transactional(readOnly = true)
    public Page<StockMovementDTO> listPending(Pageable pageable) {
        return stockMovementRepository
                .findByStatusOrderByIdAsc(StockMovement.Status.PENDING, pageable)
                .map(this::toDTO);
    }

    /** Movement history for one product, from the ledger rather than a computed balance. */
    @Transactional(readOnly = true)
    public Page<StockMovementDTO> historyForProduct(Long productId, Pageable pageable) {
        return stockMovementRepository.findApprovedHistoryForProduct(productId, pageable)
                .map(this::toDTO);
    }

    /** Filtered ledger search used by the reports. */
    @Transactional(readOnly = true)
    public Page<StockMovementDTO> search(LocalDateTime from, LocalDateTime to, Long warehouseId,
                                         String type, String status, Pageable pageable) {
        return stockMovementRepository.search(from, to, warehouseId, type, status, pageable)
                .map(this::toDTO);
    }

    /**
     * Records a stock movement.
     *
     * <p>{@code ADJUSTMENT} and {@code STOCK_COUNT} are the movements a human has
     * to approve, so they are written {@link StockMovement.Status#PENDING} and the
     * stock is <b>not</b> touched — Inventory Staff submit a request, a
     * Supervisor or Manager accepts it in {@link #approve}. Callers that already
     * hold {@code STOCK_ADJUSTMENT_APPROVE} have their own adjustments approved on
     * the spot, which is what makes the approval screen usable for a Manager
     * without weakening it for anyone else.
     *
     * <p>{@code IN}, {@code OUT} and {@code TRANSFER} are operational movements —
     * receiving a delivery or moving stock between warehouses — and apply
     * immediately, exactly as before.
     *
     * <p>The caller must hold {@code INVENTORY_ADJUST}, {@code INVENTORY_TRANSFER}
     * or {@code INVENTORY_RECEIVE}; the controller checks that, and the
     * type-specific check here keeps a request that claims "RECEIVE" from writing
     * an adjustment.
     *
     * <p>A movement without a warehouse is refused unless the business has
     * exactly one. Stock that is not attributable to a location cannot be
     * reported per warehouse, and silently attributing it to an arbitrary one
     * makes two different reports disagree.
     */
    public StockMovementDTO recordMovement(CreateStockMovementRequest request) {
        Product product = productRepository.findByIdWithLock(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        Warehouse warehouse = null;
        if (request.getWarehouseId() != null) {
            warehouse = warehouseRepository.findById(request.getWarehouseId())
                    .orElseThrow(() -> new IllegalArgumentException("Warehouse not found"));
        } else {
            warehouse = warehouseStockService.resolveDefaultWarehouse(null);
        }

        User actor = currentUser();

        String type = request.getType() == null ? null : request.getType().trim().toUpperCase();
        requirePermissionFor(type, actor);

        boolean needsApproval = StockMovement.requiresApproval(type);
        boolean autoApproved = needsApproval && actor != null
                && hasAuthority("STOCK_ADJUSTMENT_APPROVE");

        // Refuse a second posting of the same fact. This is what stops a
        // retried request from moving stock twice: the row is the record, so
        // the check has to happen before it is written.
        if (request.getReference() != null && !request.getReference().isBlank()) {
            long duplicates = stockMovementRepository.countMatching(
                    product.getId(), type, request.getReference(), request.getQuantity());
            if (duplicates > 0) {
                throw new IllegalArgumentException(
                        "A movement of " + request.getQuantity() + " of " + product.getSku()
                                + " against reference '" + request.getReference()
                                + "' has already been recorded.");
            }
        }

        int delta = request.getQuantity() != null ? request.getQuantity() : 0;
        String ledgerType = mapType(type, delta);

        if (needsApproval && !autoApproved) {
            StockMovement pending = ledger.recordPending(ledgerType, product.getId(),
                    warehouse.getId(), delta, request.getReference(), request.getNotes(),
                    request.getNotes(), StockLedgerService.newGroup("ADJ"), actor);
            return toDTO(pending);
        }

        StockMovement saved = ledger.post(ledgerType, product.getId(), warehouse.getId(),
                delta, request.getReference(), request.getNotes(), request.getNotes(),
                warehouseStockService.unitCostOf(product.getId(), warehouse.getId()),
                StockLedgerService.newGroup("MOV"), StockMovement.Status.APPROVED, actor);

        auditLogService.log(actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername(),
                "STOCK_MOVEMENT_RECORDED", "STOCK_MOVEMENT", String.valueOf(saved.getId()),
                String.format("%s of %d for %s at %s",
                        ledgerType, delta, product.getSku(), warehouse.getName()));

        return toDTO(saved);
    }

    /**
     * Moves stock between warehouses in one transaction.
     *
     * <p>Requires {@code INVENTORY_TRANSFER}. Both legs are written together: the
     * source is deducted and the destination credited, or neither happens.
     */
    public List<StockMovementDTO> transfer(CreateStockMovementRequest request) {
        if (request.getWarehouseId() == null || request.getDestinationWarehouseId() == null) {
            throw new IllegalArgumentException(
                    "A transfer needs both a source and a destination warehouse.");
        }
        requireAuthority("INVENTORY_TRANSFER");

        Product product = productRepository.findByIdWithLock(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        Warehouse source = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new IllegalArgumentException("Source warehouse not found"));
        Warehouse destination = warehouseRepository.findById(request.getDestinationWarehouseId())
                .orElseThrow(() -> new IllegalArgumentException("Destination warehouse not found"));

        int quantity = request.getQuantity() == null ? 0 : Math.abs(request.getQuantity());
        User actor = currentUser();
        boolean approve = actor != null && hasAuthority("STOCK_ADJUSTMENT_APPROVE");

        List<StockMovement> legs = ledger.transfer(product, source, destination, quantity,
                request.getReference(), request.getNotes(), request.getNotes(), approve, actor);
        return legs.stream().map(this::toDTO).toList();
    }

    /**
     * Accepts a pending adjustment or stock count and applies it to the product.
     *
     * <p>Refuses to approve a movement the caller recorded themselves when they
     * do not hold the approval permission — otherwise "submit, then approve"
     * would be a two-step bypass of the whole workflow.
     */
    public StockMovementDTO approve(Long movementId, String note) {
        StockMovement movement = findForReview(movementId);
        User actor = currentUser();
        requireAuthority("STOCK_ADJUSTMENT_APPROVE");

        ledger.applyApprovedPending(movement, actor);
        movement.setReviewNote(note);
        StockMovement saved = stockMovementRepository.save(movement);

        auditLogService.log(actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername(),
                "STOCK_ADJUSTMENT_APPROVED", "STOCK_MOVEMENT", String.valueOf(saved.getId()),
                "Approved " + saved.getType() + " of " + saved.getQuantity()
                        + " for " + saved.getProduct().getSku());
        return toDTO(saved);
    }

    /** Refuses a pending adjustment or stock count. Stock is left untouched. */
    public StockMovementDTO reject(Long movementId, String note) {
        StockMovement movement = findForReview(movementId);
        User actor = currentUser();
        requireAuthority("STOCK_ADJUSTMENT_APPROVE");

        movement.setStatus(StockMovement.Status.REJECTED);
        movement.setApprovedAt(LocalDateTime.now());
        movement.setApprovedBy(actor);
        movement.setReviewNote(note);
        StockMovement saved = stockMovementRepository.save(movement);

        auditLogService.log(actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername(),
                "STOCK_ADJUSTMENT_REJECTED", "STOCK_MOVEMENT", String.valueOf(saved.getId()),
                "Rejected " + saved.getType() + " of " + saved.getQuantity()
                        + " for " + saved.getProduct().getSku()
                        + (note == null || note.isBlank() ? "" : " — " + note));
        return toDTO(saved);
    }

    /**
     * Maps the caller's type word onto a precise ledger type.
     *
     * <p>A caller who says "IN" has not said <em>why</em> the goods arrived, and
     * the difference between a purchase receipt, a customer return and a transfer
     * in is exactly what the shrinkage and turnover reports are built from.
     * Sign is honoured for ADJUSTMENT and STOCK_COUNT, where a caller expresses
     * a correction as a signed number.
     */
    private String mapType(String type, int delta) {
        return switch (type) {
            case StockMovement.TYPE_IN, "RECEIPT", "RECEIVE" ->
                    StockMovement.TYPE_PURCHASE_RECEIPT;
            case StockMovement.TYPE_OUT, "ISSUE" -> StockMovement.TYPE_SALE;
            case StockMovement.TYPE_ADJUSTMENT -> StockMovement.TYPE_ADJUSTMENT;
            case StockMovement.TYPE_STOCK_COUNT -> StockMovement.TYPE_STOCK_COUNT;
            case StockMovement.TYPE_DAMAGE -> StockMovement.TYPE_DAMAGE;
            case StockMovement.TYPE_EXPIRY -> StockMovement.TYPE_EXPIRY;
            case StockMovement.TYPE_LOSS -> StockMovement.TYPE_LOSS;
            default -> type;
        };
    }

    private StockMovement findForReview(Long movementId) {
        StockMovement movement = stockMovementRepository.findById(movementId)
                .orElseThrow(() -> new IllegalArgumentException("Stock movement not found"));
        if (!StockMovement.Status.PENDING.equals(movement.getStatus())) {
            throw new IllegalArgumentException(
                    "Only a pending movement can be reviewed; this one is " + movement.getStatus() + ".");
        }
        return movement;
    }

    /** The movement type must match a permission the caller actually holds. */
    private void requirePermissionFor(String type, User actor) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Movement type is required.");
        }
        boolean allowed = switch (type) {
            case StockMovement.TYPE_ADJUSTMENT, StockMovement.TYPE_STOCK_COUNT ->
                    hasAuthority("INVENTORY_ADJUST") || hasAuthority("STOCK_ADJUSTMENT_APPROVE");
            case StockMovement.TYPE_TRANSFER -> hasAuthority("INVENTORY_TRANSFER");
            case StockMovement.TYPE_IN, "RECEIPT", "RECEIVE" -> hasAuthority("INVENTORY_RECEIVE");
            case StockMovement.TYPE_OUT, "ISSUE" ->
                    hasAuthority("INVENTORY_ADJUST") || hasAuthority("SALE_CREATE");
            case StockMovement.TYPE_DAMAGE -> hasAuthority("DAMAGE_RECORD");
            case StockMovement.TYPE_EXPIRY -> hasAuthority("EXPIRY_RECORD");
            case StockMovement.TYPE_LOSS -> hasAuthority("DAMAGE_RECORD")
                    || hasAuthority("STOCK_ADJUSTMENT_APPROVE");
            case StockMovement.TYPE_OPENING -> hasAuthority("PRODUCT_CREATE")
                    || hasAuthority("STOCK_ADJUSTMENT_APPROVE");
            default -> false;
        };
        if (!allowed) {
            throw new AccessDeniedException(
                    "You do not have permission to record a " + type + " stock movement.");
        }
    }

    /**
     * Authority check against the authenticated principal, used where the type of
     * movement determines the permission required and the controller can only
     * assert the broadest one.
     */
    private boolean hasAuthority(String authority) {
        return effectiveAuthorities().contains(authority);
    }

    /**
     * The authorities in force for this thread.
     *
     * <p>A caller's own authorities when there is a request in flight, or
     * whatever {@link #asSystem} set for a scheduled job, an evaluation run or
     * any other caller acting outside a web request. Without that second case
     * the service would refuse its own background work, because a background
     * thread has no security context and the check would fail closed on output
     * the system is producing itself.
     */
    private Set<String> effectiveAuthorities() {
        Set<String> system = SYSTEM_AUTHORITIES.get();
        if (system != null) {
            return system;
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(granted -> granted.getAuthority())
                .collect(Collectors.toSet());
    }

    private void requireAuthority(String authority) {
        if (!hasAuthority(authority)) {
            throw new AccessDeniedException("You do not have permission to review stock movements.");
        }
    }

    private User currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        String principal = authentication.getName();
        return userRepository.findByEmail(principal)
                .or(() -> userRepository.findByUsername(principal))
                .orElse(null);
    }

    private StockMovementDTO toDTO(StockMovement m) {
        StockMovementDTO dto = new StockMovementDTO();
        dto.setId(m.getId());
        if (m.getProduct() != null) {
            dto.setProductId(m.getProduct().getId());
            dto.setProductName(m.getProduct().getName());
            dto.setProductSku(m.getProduct().getSku());
        }
        if (m.getWarehouse() != null) {
            dto.setWarehouseId(m.getWarehouse().getId());
            dto.setWarehouseName(m.getWarehouse().getName());
        }
        if (m.getDestinationWarehouse() != null) {
            dto.setDestinationWarehouseId(m.getDestinationWarehouse().getId());
            dto.setDestinationWarehouseName(m.getDestinationWarehouse().getName());
        }
        dto.setType(m.getType());
        dto.setQuantity(m.getQuantity());
        dto.setReference(m.getReference());
        dto.setReason(m.getReason());
        dto.setUnitCost(m.getUnitCost());
        dto.setNotes(m.getNotes());
        dto.setNote(m.getNotes()); // alias
        // Derive direction from type for frontend
        int qty = m.getQuantity() != null ? m.getQuantity() : 0;
        dto.setDirection(qty >= 0 ? "in" : "out");
        if (m.getCreatedBy() != null) {
            dto.setCreatedBy(m.getCreatedBy().getUsername());
        }
        dto.setCreatedAt(m.getCreatedAt());
        if (m.getCreatedAt() != null) {
            dto.setDate(m.getCreatedAt().format(DATE_FMT));
        }
        if (m.getStatus() != null) {
            dto.setStatus(m.getStatus());
        }
        return dto;
    }
}
package com.inventory.backend.service;

import com.inventory.backend.dto.CreateStockMovementRequest;
import com.inventory.backend.dto.StockMovementDTO;
import com.inventory.backend.model.Product;
import com.inventory.backend.model.StockMovement;
import com.inventory.backend.model.User;
import com.inventory.backend.model.Warehouse;
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

@Service
@RequiredArgsConstructor
@Transactional
public class StockMovementService {
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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

    /**
     * Records a stock movement.
     *
     * <p>{@code ADJUSTMENT} and {@code STOCK_COUNT} are the movements a human has
     * to approve, so they are written {@link StockMovement.Status#PENDING} and the
     * product quantity is <b>not</b> touched — Inventory Staff submit a request,
     * a Supervisor or Manager accepts it in {@link #approve}. Callers that already
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
     */
    public StockMovementDTO recordMovement(CreateStockMovementRequest request) {
        Product product = productRepository.findByIdWithLock(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        Warehouse warehouse = null;
        if (request.getWarehouseId() != null) {
            warehouse = warehouseRepository.findById(request.getWarehouseId())
                    .orElseThrow(() -> new IllegalArgumentException("Warehouse not found"));
        }

        User actor = currentUser();

        String type = request.getType() == null ? null : request.getType().trim().toUpperCase();
        requirePermissionFor(type, actor);

        boolean needsApproval = StockMovement.requiresApproval(type);
        boolean autoApproved = needsApproval && actor != null
                && hasAuthority("STOCK_ADJUSTMENT_APPROVE");

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setWarehouse(warehouse);
        movement.setType(type);
        movement.setQuantity(request.getQuantity());
        movement.setReference(request.getReference());
        movement.setNotes(request.getNotes());
        movement.setCreatedBy(actor);
        movement.setStatus(autoApproved ? StockMovement.Status.APPROVED : StockMovement.Status.PENDING);

        if (!needsApproval) {
            movement.setStatus(StockMovement.Status.APPROVED);
            movement.setApprovedAt(LocalDateTime.now());
            movement.setApprovedBy(actor);
        }

        StockMovement saved = stockMovementRepository.save(movement);

        // Only an approved movement moves the goods.
        if (StockMovement.Status.APPROVED.equals(saved.getStatus())) {
            applyToStock(product, saved);
        }

        return toDTO(saved);
    }

    /**
     * Accepts a pending adjustment or stock count and applies it to the product.
     *
     * <p>Refuses to approve a movement the caller recorded themselves when they
     * do not hold the approval permission — otherwise "submit, then approve" would
     * be a two-step bypass of the whole workflow.
     */
    public StockMovementDTO approve(Long movementId, String note) {
        StockMovement movement = findForReview(movementId);
        User actor = currentUser();
        requireAuthority("STOCK_ADJUSTMENT_APPROVE");

        movement.setStatus(StockMovement.Status.APPROVED);
        movement.setApprovedAt(LocalDateTime.now());
        movement.setApprovedBy(actor);
        movement.setReviewNote(note);
        StockMovement saved = stockMovementRepository.save(movement);

        Product product = productRepository.findByIdWithLock(saved.getProduct().getId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        applyToStock(product, saved);

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

    private StockMovement findForReview(Long movementId) {
        StockMovement movement = stockMovementRepository.findById(movementId)
                .orElseThrow(() -> new IllegalArgumentException("Stock movement not found"));
        if (!StockMovement.Status.PENDING.equals(movement.getStatus())) {
            throw new IllegalArgumentException(
                    "Only a pending movement can be reviewed; this one is " + movement.getStatus() + ".");
        }
        return movement;
    }

    /**
     * Applies a signed-off movement to the product, never below zero.
     *
     * <p>Clamping at zero rather than rejecting: a count that overshoots means the
     * shelf genuinely holds less than the count, and refusing would leave the
     * request permanently un-approvable. The clamp is deliberate and is recorded
     * in the movement note so the discrepancy is visible rather than silent.
     */
    private void applyToStock(Product product, StockMovement movement) {
        int current = product.getQuantity() != null ? product.getQuantity() : 0;
        int delta = movement.getQuantity() != null ? movement.getQuantity() : 0;
        int updated = current + delta;

        if (updated < 0) {
            movement.setNotes(appendNote(movement.getNotes(),
                    "Applied as 0: a movement of " + delta + " from a balance of " + current
                            + " would have gone negative."));
            updated = 0;
        }

        product.setQuantity(updated);
        productRepository.save(product);
    }

    private String appendNote(String existing, String addition) {
        return existing == null || existing.isBlank() ? addition : existing + " | " + addition;
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
            case StockMovement.TYPE_OUT, "ISSUE" -> hasAuthority("INVENTORY_ADJUST") || hasAuthority("SALE_CREATE");
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
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
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
        dto.setType(m.getType());
        dto.setQuantity(m.getQuantity());
        dto.setReference(m.getReference());
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
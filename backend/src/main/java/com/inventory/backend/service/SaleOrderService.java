package com.inventory.backend.service;

import com.inventory.backend.dto.*;
import com.inventory.backend.model.*;
import com.inventory.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleOrderService {

    private final SaleOrderRepository saleOrderRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final AuditLogService auditLogService;
    private final StockLedgerService ledger;
    private final WarehouseStockService warehouseStockService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * The sales the caller is allowed to see.
     *
     * <p>{@code SALE_READ} alone means "sales you can look at", not "every sale
     * in the business". Holding {@code SALES_REPORT_VIEW} is what widens it: that
     * permission exists precisely to see the whole picture, so a Cashier — who
     * has the first but not the second — sees only their own transactions and
     * everyone else's takings stay out of reach.
     *
     * <p>This is enforced here rather than by hiding the column in the UI,
     * because a Cashier with a valid token can call this endpoint directly.
     */
    @Transactional(readOnly = true)
    public Page<SaleOrderDTO> listSales(Pageable pageable) {
        User actor = currentUser();

        if (!canSeeEveryonesSales(actor)) {
            // An anonymous caller sees nothing; a real one sees their own page,
            // with a count computed over their own rows. Reusing the table-wide
            // count would leak the size of the ledger through the pagination
            // metadata even when every row is filtered out of the page.
            if (actor == null) {
                return new PageImpl<>(List.of(), pageable, 0);
            }
            return saleOrderRepository.findBySellerWithDetails(actor.getId(), pageable)
                    .map(this::toDTO);
        }

        // Use fetch join to avoid N+1 queries when accessing items, products, customers
        List<SaleOrder> orders = saleOrderRepository.findAllWithDetails(pageable);
        List<SaleOrderDTO> dtos = orders.stream().map(this::toDTO).collect(Collectors.toList());

        // Get total count for pagination metadata
        long total = saleOrderRepository.count();
        return new PageImpl<>(dtos, pageable, total);
    }

    /**
     * Whether this caller may read sales recorded by other people.
     *
     * <p>Super Admin resolves to every permission, so it qualifies without a
     * special case here.
     */
    private boolean canSeeEveryonesSales(User actor) {
        return holdsAuthority("SALES_REPORT_VIEW") || holdsAuthority("REPORT_VIEW");
    }

    /** Whether the caller's token carries an authority, Super Admin included. */
    private boolean holdsAuthority(String authority) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    /**
     * One sale, if the caller is allowed to see it.
     *
     * <p>Same rule as {@link #listSales}: without the wider reporting
     * permission, a sale that belongs to somebody else is reported as missing
     * rather than returned. Saying "not found" rather than "forbidden" avoids
     * confirming that a given order number exists to someone who may not see it.
     */
    @Transactional(readOnly = true)
    public SaleOrderDTO getSale(Long id) {
        SaleOrder order = saleOrderRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Sale not found: id=" + id));

        requireVisibleSale(currentUser(), order);
        return toDTO(order);
    }

    // ── Void and refund approvals ──────────────────────────────────────────────
    // A cashier may need to undo a sale, but may not do so unilaterally: the
    // goods are already off the shelf and the money may already be banked. So the
    // cashier raises a request and a Supervisor or Manager decides. The request
    // state lives on the order rather than in a side table, because there is
    // exactly one open void and one open refund per sale and both need to be
    // visible on the order the cashier is looking at.

    /**
     * Sales awaiting a void or refund decision.
     *
     * <p>Read-only: this backs a screen, and the decision itself goes through
     * {@link #approveVoid}/{@link #rejectVoid} and the refund equivalents, which
     * re-check that the request is still pending. Listing is deliberately not an
     * approval, so seeing the queue changes nothing.
     */
    @Transactional(readOnly = true)
    public Page<SaleOrderDTO> listAwaitingApproval(Pageable pageable) {
        return saleOrderRepository
                .findAwaitingApproval(StockMovement.Status.PENDING, pageable)
                .map(this::toDTO);
    }

    /**
     * Raises a request to void a sale. Requires {@code SALE_VOID_REQUEST};
     * accepting it requires {@code SALE_VOID_APPROVE}.
     */
    @Transactional
    public SaleOrderDTO requestVoid(Long id, String reason) {
        SaleOrder order = loadForApproval(id);
        User actor = currentUser();
        requireVisibleSale(actor, order);
        order.setVoidStatus(StockMovement.Status.PENDING);
        order.setVoidReason(reason);
        order.setVoidRequestedBy(actor);
        order.setVoidRequestedAt(java.time.LocalDateTime.now());
        SaleOrder saved = saleOrderRepository.save(order);
        logApproval(actor, "SALE_VOID_REQUESTED", saved, reason);
        return toDTO(saved);
    }

    /**
     * Accepts a pending void: the sale is cancelled and its stock goes back.
     *
     * <p>Restocking uses the same per-warehouse stock service as a sale, so the
     * ledger and the warehouse split cannot drift apart when a sale is undone.
     */
    @Transactional
    public SaleOrderDTO approveVoid(Long id, String note) {
        SaleOrder order = loadForApproval(id);
        requirePending(order.getVoidStatus(), "void");
        User actor = currentUser();

        restock(order, actor);

        order.setStatus("CANCELLED");
        order.setPaymentStatus("REFUNDED");
        order.setVoidStatus(StockMovement.Status.APPROVED);
        order.setVoidApprovedBy(actor);
        order.setVoidApprovedAt(java.time.LocalDateTime.now());
        order.setVoidReviewNote(note);
        SaleOrder saved = saleOrderRepository.save(order);

        logApproval(actor, "SALE_VOID_APPROVED", saved, note);
        return toDTO(saved);
    }

    /** Refuses a pending void. The sale and its stock are untouched. */
    @Transactional
    public SaleOrderDTO rejectVoid(Long id, String note) {
        SaleOrder order = loadForApproval(id);
        requirePending(order.getVoidStatus(), "void");
        User actor = currentUser();
        order.setVoidStatus(StockMovement.Status.REJECTED);
        order.setVoidApprovedBy(actor);
        order.setVoidApprovedAt(java.time.LocalDateTime.now());
        order.setVoidReviewNote(note);
        SaleOrder saved = saleOrderRepository.save(order);
        logApproval(actor, "SALE_VOID_REJECTED", saved, note);
        return toDTO(saved);
    }

    /** Raises a request to refund a sale. Requires {@code SALE_REFUND_REQUEST}. */
    @Transactional
    public SaleOrderDTO requestRefund(Long id, String reason) {
        SaleOrder order = loadForApproval(id);
        User actor = currentUser();
        requireVisibleSale(actor, order);
        order.setRefundStatus(StockMovement.Status.PENDING);
        order.setRefundReason(reason);
        order.setRefundRequestedBy(actor);
        order.setRefundRequestedAt(java.time.LocalDateTime.now());
        SaleOrder saved = saleOrderRepository.save(order);
        logApproval(actor, "SALE_REFUND_REQUESTED", saved, reason);
        return toDTO(saved);
    }

    /**
     * Accepts a pending refund: stock goes back and the payment is marked
     * refunded. The sale itself stays on record rather than being deleted —
     * a refunded sale is still a sale that happened.
     */
    @Transactional
    public SaleOrderDTO approveRefund(Long id, String note) {
        SaleOrder order = loadForApproval(id);
        requirePending(order.getRefundStatus(), "refund");
        User actor = currentUser();

        restock(order, actor);

        order.setPaymentStatus("REFUNDED");
        order.setRefundStatus(StockMovement.Status.APPROVED);
        order.setRefundApprovedBy(actor);
        order.setRefundApprovedAt(java.time.LocalDateTime.now());
        order.setRefundReviewNote(note);
        SaleOrder saved = saleOrderRepository.save(order);

        logApproval(actor, "SALE_REFUND_APPROVED", saved, note);
        return toDTO(saved);
    }

    /** Refuses a pending refund. Nothing changes. */
    @Transactional
    public SaleOrderDTO rejectRefund(Long id, String note) {
        SaleOrder order = loadForApproval(id);
        requirePending(order.getRefundStatus(), "refund");
        User actor = currentUser();
        order.setRefundStatus(StockMovement.Status.REJECTED);
        order.setRefundApprovedBy(actor);
        order.setRefundApprovedAt(java.time.LocalDateTime.now());
        order.setRefundReviewNote(note);
        SaleOrder saved = saleOrderRepository.save(order);
        logApproval(actor, "SALE_REFUND_REJECTED", saved, note);
        return toDTO(saved);
    }

    /**
     * Refuses a sale the caller is not entitled to see.
     *
     * <p>Both request endpoints return the whole order DTO. Without this a
     * Cashier who guessed or was handed an id could read a colleague's customer
     * name and takings through the void-request response — the very data
     * {@link #listSales} withholds from them.
     */
    private void requireVisibleSale(User actor, SaleOrder order) {
        if (canSeeEveryonesSales(actor)) {
            return;
        }
        boolean ownSale = actor != null
                && order.getCreatedBy() != null
                && order.getCreatedBy().getId().equals(actor.getId());
        if (!ownSale) {
            throw new jakarta.persistence.EntityNotFoundException(
                    "Sale not found: id=" + order.getId());
        }
    }

    private SaleOrder loadForApproval(Long id) {
        return saleOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: id=" + id));
    }

    private void requirePending(String currentStatus, String what) {
        if (!StockMovement.Status.PENDING.equals(currentStatus)) {
            throw new IllegalArgumentException(
                    "This sale has no pending " + what + " request (status: " + currentStatus + ").");
        }
    }

    /**
     * Returns each sold item to the warehouse it was taken from.
     *
     * <p>Going back to the original warehouse rather than "wherever stock is
     * available" is deliberate: putting it somewhere else would silently
     * relocate the business's inventory as a side effect of a refund.
     */
    private void restock(SaleOrder order, User actor) {
        if (order.getItems() == null) {
            return;
        }
        Warehouse warehouse = order.getWarehouse();
        if (warehouse == null) {
            warehouse = warehouseStockService.resolveDefaultWarehouse(null);
        }
        for (SaleOrderItem item : order.getItems()) {
            if (item.getProduct() == null || item.getQuantity() == null) {
                continue;
            }
            int returned = item.getQuantityReturned() != null ? item.getQuantityReturned() : 0;
            int quantity = item.getQuantity() - returned;
            if (quantity <= 0) {
                continue;
            }
            // The cost comes back with the goods, at the cost they left at.
            BigDecimal unitCost = item.getUnitCost() != null
                    ? item.getUnitCost()
                    : (item.getProduct().getPurchasePrice() != null
                        ? item.getProduct().getPurchasePrice() : BigDecimal.ZERO);

            ledger.post(StockMovement.TYPE_SALES_RETURN, item.getProduct().getId(),
                    warehouse.getId(), quantity, order.getOrderNumber(),
                    "Sale voided or refunded",
                    "Returned to stock — sale " + order.getOrderNumber()
                            + " voided or refunded",
                    unitCost, StockLedgerService.newGroup("RET-" + order.getId()),
                    StockMovement.Status.APPROVED, actor);
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

    private void logApproval(User actor, String action, SaleOrder order, String note) {
        auditLogService.log(actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername(),
                action, "SALE", String.valueOf(order.getId()),
                order.getOrderNumber() + (note == null || note.isBlank() ? "" : " — " + note));
    }

    /**
     * Records a sale: deducts stock from the stated warehouse, snapshots the
     * cost of the goods, and writes the payment that was actually taken.
     *
     * <p>Everything happens in one transaction. If the payment cannot be written
     * the stock comes back, because a sale that moved goods without recording
     * the money is worse than a sale that failed.
     */
    @Transactional
    public SaleOrderDTO createSale(CreateSaleOrderRequest req) {
        // ── 0. Idempotency: return the existing sale on duplicate submission ──
        if (req.getIdempotencyKey() != null && !req.getIdempotencyKey().isBlank()) {
            var existing = saleOrderRepository.findByIdempotencyKey(req.getIdempotencyKey());
            if (existing.isPresent()) {
                return toDTO(existing.get());
            }
        }

        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new IllegalArgumentException("A sale must contain at least one item.");
        }

        User currentUserEntity = currentUser();

        // ── 1. Build the order shell ──────────────────────────────────────────
        SaleOrder order = new SaleOrder();
        order.setOrderNumber("SO-TMP-" + java.util.UUID.randomUUID()); // placeholder; replaced with stable number below
        if (req.getIdempotencyKey() != null && !req.getIdempotencyKey().isBlank()) {
            order.setIdempotencyKey(req.getIdempotencyKey());
        }

        // Customer: link the real customer record when an id is provided
        if (req.getCustomerId() != null) {
            Customer customer = customerRepository.findById(req.getCustomerId())
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found: id=" + req.getCustomerId()));
            order.setCustomer(customer);
            order.setCustomerName((req.getCustomerName() != null && !req.getCustomerName().isBlank())
                    ? req.getCustomerName() : customer.getName());
        } else {
            order.setCustomerName((req.getCustomerName() != null && !req.getCustomerName().isBlank())
                    ? req.getCustomerName() : "Walk-in Customer");
        }

        // Which warehouse the goods leave. Resolved before anything moves, so an
        // ambiguous warehouse fails the sale rather than half-completing it.
        Warehouse warehouse = warehouseStockService.resolveDefaultWarehouse(req.getWarehouseId());
        order.setWarehouse(warehouse);

        order.setCreatedBy(currentUserEntity);
        order.setDiscount(req.getDiscount() != null ? req.getDiscount() : BigDecimal.ZERO);
        order.setTax(req.getTax() != null ? req.getTax() : BigDecimal.ZERO);
        order.setPaymentMethod(req.getPaymentMethod() != null ? req.getPaymentMethod() : "CASH");
        order.setTransactionDate(LocalDateTime.now());

        // Determine payment status from request; default to PAID (cash sale)
        String paymentStatus = req.getPaymentStatus() != null ? req.getPaymentStatus() : "PAID";
        order.setPaymentStatus(paymentStatus);
        order.setStatus("UNPAID".equals(paymentStatus) ? "PENDING" : "PAID");

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        List<SaleOrderItem> items = new ArrayList<>();

        // ── 2. Validate stock, price the line, and move the goods ────────────
        //
        // The cost is snapshotted per line before the goods leave, so the COGS of
        // this sale is fixed now and is not restated when the product's purchase
        // price changes later.
        for (CreateSaleOrderItem i : req.getItems()) {
            Product p = productRepository.findByIdWithLock(i.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: id=" + i.getProductId()));

            if (i.getQuantity() == null || i.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be at least 1 for product: " + p.getName());
            }
            if (i.getUnitPrice() == null) {
                throw new IllegalArgumentException("A selling price is required for product: " + p.getName());
            }

            // The cost is snapshotted per line before the goods leave, so the COGS
            // of this sale is fixed now and is not restated when the product's
            // purchase price changes later.
            BigDecimal unitCost = warehouseStockService.unitCostOf(p.getId(), warehouse.getId());
            if (unitCost == null) {
                unitCost = p.getPurchasePrice() != null ? p.getPurchasePrice() : BigDecimal.ZERO;
            }

            // Up-front availability check, so the caller gets a clear message
            // before any work is done. The authoritative check is the one inside
            // applyMovement, which holds the row lock and is what actually
            // prevents two concurrent sales from both succeeding.
            int available = warehouseStockService.quantityIn(p.getId(), warehouse.getId());
            if (available < i.getQuantity()) {
                throw new IllegalArgumentException(String.format(
                        "Insufficient stock for '%s' in %s. Available: %d, Requested: %d.",
                        p.getName(), warehouse.getName(), available, i.getQuantity()));
            }

            BigDecimal itemDiscount = i.getDiscount() != null ? i.getDiscount() : BigDecimal.ZERO;
            BigDecimal lineTotal = com.inventory.backend.finance.FinancialCalculations
                    .lineNet(i.getUnitPrice(), i.getQuantity(), itemDiscount);

            SaleOrderItem item = new SaleOrderItem();
            item.setSaleOrder(order);
            item.setProduct(p);
            item.setQuantity(i.getQuantity());
            item.setUnitPrice(i.getUnitPrice());
            item.setDiscount(itemDiscount);
            item.setSubtotal(lineTotal);
            item.setUnitCost(unitCost);
            item.setQuantityReturned(0);
            items.add(item);

            subtotal = subtotal.add(lineTotal);
            cogs = cogs.add(unitCost.multiply(BigDecimal.valueOf(i.getQuantity())));
        }

        order.setTotalAmount(subtotal);
        order.setFinalAmount(subtotal.subtract(order.getDiscount()).add(order.getTax()));
        order.setCostOfGoodsSold(cogs);
        order.setAmountPaid(order.getFinalAmount());
        order.setItems(items);

        SaleOrder saved = saleOrderRepository.save(order);
        // Stable, unique, human-friendly sale number derived from the DB id
        saved.setOrderNumber(String.format("SO-%06d", saved.getId()));
        saved = saleOrderRepository.save(saved);

        // ── 4. Record the ledger entry per line, which is also what deducts the
        //       stock from the warehouse ───────────────────────────────────────
        // Deliberately not applied above as well. The ledger is the single place
        // a movement is written and the single place stock changes; calling both
        // would deduct the goods twice and leave the ledger disagreeing with the
        // quantity on hand.
        String group = StockLedgerService.newGroup("SALE-" + saved.getId());
        for (SaleOrderItem item : saved.getItems()) {
            ledger.post(StockMovement.TYPE_SALE, item.getProduct().getId(), warehouse.getId(),
                    -item.getQuantity(), saved.getOrderNumber(), "Customer sale",
                    "Sale: " + saved.getOrderNumber() + " at " + warehouse.getName(),
                    item.getUnitCost(), group, StockMovement.Status.APPROVED, currentUserEntity);
        }

        // ── 5. Record the payment that was actually taken ─────────────────────
        //
        // Only money that was handed over gets a COMPLETED payment. A credit sale
        // gets a PENDING one so the amount is on record without being counted as
        // cash, which is what distinguishes "sold" from "paid".
        Payment payment = Payment.builder()
                .reference("PAY-" + System.currentTimeMillis() + "-" + saved.getId())
                .orderReference(saved.getOrderNumber())
                .type(Payment.DIRECTION_IN)
                .direction(Payment.DIRECTION_IN)
                .partyType(Payment.PARTY_CUSTOMER)
                .customer(saved.getCustomer())
                .saleOrder(saved)
                .amount(saved.getFinalAmount())
                .paymentMethod(saved.getPaymentMethod())
                .status("UNPAID".equals(paymentStatus)
                        ? Payment.STATUS_PENDING : Payment.STATUS_COMPLETED)
                .transactionDate(LocalDateTime.now())
                .createdBy(currentUserEntity == null ? "system" : currentUserEntity.getUsername())
                .createdByUser(currentUserEntity)
                .build();
        paymentRepository.save(payment);

        if ("UNPAID".equals(paymentStatus)) {
            saved.setAmountPaid(BigDecimal.ZERO);
            saleOrderRepository.save(saved);
        }

        return toDTO(saved);
    }

    /**
     * A person's name for display, falling back to their username.
     *
     * <p>{@code User.getFullName()} concatenates unconditionally, so a user with
     * no first or last name yields the literal string "null null" — which would
     * then be rendered in a sales report as though it were a name. Falls back
     * through first+last, then either one, then the username, so a row always
     * identifies a person.
     */
    private String displayName(User user) {
        if (user == null) {
            return null;
        }
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String full = (first + " " + last).trim();
        if (!full.isEmpty() && !full.equals("null")) {
            return full;
        }
        return user.getUsername();
    }

    public SaleOrderDTO toDTO(SaleOrder o) {
        SaleOrderDTO dto = new SaleOrderDTO();
        dto.setId(o.getId());
        dto.setOrderNumber(o.getOrderNumber());
        dto.setReference(o.getOrderNumber());   // frontend alias
        dto.setCustomerName(o.getCustomerName());
        dto.setTotalAmount(o.getTotalAmount());
        dto.setDiscount(o.getDiscount() != null ? o.getDiscount() : BigDecimal.ZERO);
        dto.setFinalAmount(o.getFinalAmount());
        dto.setTotal(o.getFinalAmount());        // frontend alias
        dto.setStatus(o.getStatus());
        dto.setPaymentMethod(o.getPaymentMethod());
        dto.setCreatedAt(o.getCreatedAt());
        dto.setVoidStatus(o.getVoidStatus());
        dto.setVoidReason(o.getVoidReason());
        dto.setVoidReviewNote(o.getVoidReviewNote());
        dto.setRefundStatus(o.getRefundStatus());
        dto.setRefundReason(o.getRefundReason());
        dto.setRefundReviewNote(o.getRefundReviewNote());
        dto.setTax(o.getTax() != null ? o.getTax() : BigDecimal.ZERO);
        if (o.getCustomer() != null) {
            dto.setCustomerId(o.getCustomer().getId());
        }
        if (o.getWarehouse() != null) {
            dto.setWarehouseId(o.getWarehouse().getId());
            dto.setWarehouseName(o.getWarehouse().getName());
        }
        if (o.getCreatedBy() != null) {
            dto.setCreatedBy(o.getCreatedBy().getUsername());
            // Cashier and branch travel together because branch is an attribute
            // of the user, not of the order: whoever rang it up is where it was
            // rung. Sending the name with the order keeps the list screen from
            // having to resolve either one per row.
            dto.setCashier(new RefDTO(o.getCreatedBy().getId(),
                    displayName(o.getCreatedBy())));
            String branch = o.getCreatedBy().getBranch();
            if (branch != null && !branch.isBlank()) {
                // No id: branch is free text on the user row, so there is no
                // surrogate key to report until it becomes an entity.
                dto.setBranch(new RefDTO(null, branch.trim()));
            }
        }
        // derive date string for frontend
        if (o.getCreatedAt() != null) {
            dto.setDate(o.getCreatedAt().format(DATE_FMT));
        }
        // derive payment status — trust the stored field, fall back to status
        if (o.getPaymentStatus() != null && !o.getPaymentStatus().isBlank()) {
            dto.setPaymentStatus(o.getPaymentStatus());
        } else {
            dto.setPaymentStatus(derivePaymentStatus(o));
        }
        // Amount actually received, from the stored running total. Reporting the
        // invoice total as "paid" for every sale would make every credit sale
        // look collected.
        dto.setPaid(o.getAmountPaid() != null ? o.getAmountPaid() : BigDecimal.ZERO);
        dto.setCostOfGoodsSold(o.getCostOfGoodsSold() != null
                ? o.getCostOfGoodsSold() : BigDecimal.ZERO);

        // Map items
        if (o.getItems() != null) {
            dto.setItems(o.getItems().stream().map(i -> {
                SaleOrderItemDTO idto = new SaleOrderItemDTO();
                idto.setId(i.getId());
                if (i.getProduct() != null) {
                    idto.setProductId(i.getProduct().getId());
                    idto.setProductName(i.getProduct().getName());
                    idto.setProductSku(i.getProduct().getSku());
                }
                idto.setQuantity(i.getQuantity());
                idto.setUnitPrice(i.getUnitPrice());
                idto.setSubtotal(i.getSubtotal());
                idto.setTotal(i.getSubtotal());    // frontend alias
                idto.setDiscount(i.getDiscount() != null ? i.getDiscount() : BigDecimal.ZERO);
                idto.setUnitCost(i.getUnitCost());
                return idto;
            }).collect(Collectors.toList()));
        }

        return dto;
    }

    private String derivePaymentStatus(SaleOrder o) {
        if ("PAID".equals(o.getStatus())) return "PAID";
        if ("CANCELLED".equals(o.getStatus())) return "UNPAID";
        return "UNPAID";
    }
}
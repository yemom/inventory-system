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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * The sales the caller is allowed to see.
     *
     * <p>{@code SALE_READ} alone means \"sales you can look at\", not \"every sale
     * in the business\". Holding {@code SALES_REPORT_VIEW} is what widens it: that
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
     * rather than returned. Saying \"not found\" rather than \"forbidden\" avoids
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
     * <p>Restocking uses the same lock as a sale so a void cannot race a
     * concurrent sale into a negative quantity.
     */
    @Transactional
    public SaleOrderDTO approveVoid(Long id, String note) {
        SaleOrder order = loadForApproval(id);
        requirePending(order.getVoidStatus(), "void");
        User actor = currentUser();

        restock(order);

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

        restock(order);

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

    /** Returns each sold item to stock, never below zero, and records why. */
    private void restock(SaleOrder order) {
        if (order.getItems() == null) {
            return;
        }
        for (SaleOrderItem item : order.getItems()) {
            if (item.getProduct() == null || item.getQuantity() == null) {
                continue;
            }
            Product product = productRepository.findByIdWithLock(item.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Product not found: id=" + item.getProduct().getId()));
            int current = product.getQuantity() != null ? product.getQuantity() : 0;
            product.setQuantity(current + item.getQuantity());
            productRepository.save(product);

            stockMovementRepository.save(StockMovement.builder()
                    .product(product)
                    .type("IN")
                    .quantity(item.getQuantity())
                    .reference(order.getOrderNumber())
                    .notes("Returned to stock — sale " + order.getOrderNumber() + " voided or refunded")
                    .createdBy(currentUser())
                    .status(StockMovement.Status.APPROVED)
                    .approvedAt(java.time.LocalDateTime.now())
                    .build());
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

        String principal;
        try {
            principal = SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            principal = null;
        }
        final String currentUser = principal;
        User currentUserEntity = (currentUser == null) ? null :
                userRepository.findByEmail(currentUser)
                        .or(() -> userRepository.findByUsername(currentUser))
                        .orElse(null);

        // ── 1. Build the order shell ──────────────────────────────────────────
        SaleOrder order = new SaleOrder();
        order.setOrderNumber("SO-TMP-" + UUID.randomUUID()); // placeholder; replaced with stable number below
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

        order.setCreatedBy(currentUserEntity);
        order.setDiscount(req.getDiscount() != null ? req.getDiscount() : BigDecimal.ZERO);
        order.setTax(req.getTax() != null ? req.getTax() : BigDecimal.ZERO);
        order.setPaymentMethod(req.getPaymentMethod() != null ? req.getPaymentMethod() : "CASH");

        // Determine payment status from request; default to PAID (cash sale)
        String paymentStatus = req.getPaymentStatus() != null ? req.getPaymentStatus() : "PAID";
        order.setPaymentStatus(paymentStatus);
        order.setStatus("UNPAID".equals(paymentStatus) ? "PENDING" : "PAID");

        BigDecimal subtotal = BigDecimal.ZERO;
        List<SaleOrderItem> items = new ArrayList<>();

        // ── 2. Validate stock and build items ─────────────────────────────────
        for (CreateSaleOrderItem i : req.getItems()) {
            Product p = productRepository.findByIdWithLock(i.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: id=" + i.getProductId()));

            if (i.getQuantity() == null || i.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be at least 1 for product: " + p.getName());
            }

            int currentStock = p.getQuantity() != null ? p.getQuantity() : 0;
            if (currentStock < i.getQuantity()) {
                throw new IllegalArgumentException(
                        "Insufficient stock for '" + p.getName() + "'. " +
                        "Available: " + currentStock + ", Requested: " + i.getQuantity());
            }

            // ── 3. Deduct stock atomically ────────────────────────────────────
            p.setQuantity(currentStock - i.getQuantity());
            productRepository.save(p);

            BigDecimal itemDiscount = i.getDiscount() != null ? i.getDiscount() : BigDecimal.ZERO;
            BigDecimal lineTotal = i.getUnitPrice().multiply(new BigDecimal(i.getQuantity())).subtract(itemDiscount);

            SaleOrderItem item = new SaleOrderItem();
            item.setSaleOrder(order);
            item.setProduct(p);
            item.setQuantity(i.getQuantity());
            item.setUnitPrice(i.getUnitPrice());
            item.setDiscount(itemDiscount);
            item.setSubtotal(lineTotal);
            items.add(item);

            subtotal = subtotal.add(item.getSubtotal());
        }

        order.setTotalAmount(subtotal);
        order.setFinalAmount(subtotal.subtract(order.getDiscount()).add(order.getTax()));
        order.setItems(items);

        SaleOrder saved = saleOrderRepository.save(order);
        // Stable, unique, human-friendly sale number derived from the DB id
        saved.setOrderNumber(String.format("SO-%06d", saved.getId()));
        saved = saleOrderRepository.save(saved);

        // ── 4. Record stock movement per item ─────────────────────────────────
        for (SaleOrderItem item : saved.getItems()) {
            StockMovement movement = StockMovement.builder()
                    .product(item.getProduct())
                    .type("OUT")
                    .quantity(-item.getQuantity())
                    .reference(saved.getOrderNumber())
                    .notes("Sale: " + saved.getOrderNumber())
                    .createdBy(currentUserEntity)
                    .build();
            stockMovementRepository.save(movement);
        }

        // ── 5. Record the payment ─────────────────────────────────────────────
        Payment payment = Payment.builder()
                .reference("PAY-" + System.currentTimeMillis() + "-" + saved.getId())
                .orderReference(saved.getOrderNumber())
                .type("IN")
                .amount(saved.getFinalAmount())
                .paymentMethod(saved.getPaymentMethod())
                .status("UNPAID".equals(paymentStatus) ? "PENDING" : "COMPLETED")
                .createdBy(currentUser)
                .build();
        paymentRepository.save(payment);

        return toDTO(saved);
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
        if (o.getCreatedBy() != null) {
            dto.setCreatedBy(o.getCreatedBy().getUsername());
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
        dto.setPaid(dto.getPaymentStatus().equals("PAID") ? o.getFinalAmount() : BigDecimal.ZERO);

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

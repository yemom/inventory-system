package com.inventory.backend.service;

import com.inventory.backend.dto.*;
import com.inventory.backend.model.*;
import com.inventory.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    /** Raised but not yet received: no stock has moved. */
    public static final String STATUS_DRAFT = "DRAFT";
    /** Goods have arrived and stock has been increased. */
    public static final String STATUS_RECEIVED = "RECEIVED";
    private final StockMovementRepository stockMovementRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Transactional(readOnly = true)
    public Page<PurchaseOrderDTO> listPurchases(Pageable pageable) {
        // Use fetch join to avoid N+1 queries when accessing items and products
        List<PurchaseOrder> orders = purchaseOrderRepository.findAllWithDetails(pageable);
        List<PurchaseOrderDTO> dtos = orders.stream().map(this::toDTO).collect(Collectors.toList());

        // Get total count for pagination metadata
        long total = purchaseOrderRepository.count();
        return new PageImpl<>(dtos, pageable, total);
    }

    @Transactional
    public PurchaseOrderDTO createPurchase(CreatePurchaseOrderRequest req) {
        // ── 1. Build order shell ──────────────────────────────────────────────
        PurchaseOrder order = new PurchaseOrder();
        order.setSupplierName(req.getSupplierName());
        // Derived from the saved id, like sale numbers. The previous
        // "PO-" + currentTimeMillis() had no uniqueness retry against a unique
        // column, so two orders created in the same millisecond collided.
        order.setOrderNumber("PO-TMP-" + java.util.UUID.randomUUID());

        // The status is decided here, not taken from the request.
        //
        // A caller used to be able to POST status:"PENDING" and get an order that
        // could never receive stock, because nothing could later move it to
        // RECEIVED; or POST status:"RECEIVED" and inflate stock with no approval.
        // Stock is now added only when this method itself receives the goods, and
        // that happens when the caller holds PURCHASE_APPROVE. Creating an order
        // is proposing one, so the default is DRAFT and no stock moves.
        boolean receiveNow = hasAuthority("PURCHASE_APPROVE");
        String status = receiveNow ? STATUS_RECEIVED : STATUS_DRAFT;
        order.setStatus(status);

        BigDecimal total = BigDecimal.ZERO;
        List<PurchaseOrderItem> items = new ArrayList<>();

        // ── 2. Build items ────────────────────────────────────────────────────
        for (CreatePurchaseOrderItem i : req.getItems()) {
            Product p = productRepository.findByIdWithLock(i.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: id=" + i.getProductId()));

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(order);
            item.setProduct(p);
            item.setQuantity(i.getQuantity());
            item.setUnitCost(i.getUnitCost());
            item.setSubtotal(i.getUnitCost().multiply(new BigDecimal(i.getQuantity())));
            items.add(item);

            total = total.add(item.getSubtotal());

            // ── 3. Increase stock only when this order is being received ───────
            if (receiveNow) {
                int currentStock = p.getQuantity() != null ? p.getQuantity() : 0;
                p.setQuantity(currentStock + i.getQuantity());
                productRepository.save(p);
            }
        }

        order.setTotalAmount(total);
        order.setItems(items);

        PurchaseOrder saved = purchaseOrderRepository.save(order);
        saved.setOrderNumber(String.format("PO-%06d", saved.getId()));
        saved = purchaseOrderRepository.save(saved);

        // ── 4. Record stock movements when received ────────────────────────────
        if (receiveNow) {
            User actor = currentUser();
            for (PurchaseOrderItem item : saved.getItems()) {
                stockMovementRepository.save(StockMovement.builder()
                        .product(item.getProduct())
                        .type("IN")
                        .quantity(item.getQuantity())
                        .reference(saved.getOrderNumber())
                        .notes("Purchase received: " + saved.getOrderNumber())
                        .createdBy(actor)
                        .status(StockMovement.Status.APPROVED)
                        .approvedAt(java.time.LocalDateTime.now())
                        .approvedBy(actor)
                        .build());
            }
        }

        return toDTO(saved);
    }

    /**
     * Receives a DRAFT purchase order: adds the stock and marks it RECEIVED.
     *
     * <p>Requires {@code PURCHASE_APPROVE}, so the person who raised the order is
     * not the person who makes the goods appear on the shelf.
     */
    @Transactional
    public PurchaseOrderDTO receivePurchase(Long id) {
        PurchaseOrder order = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Purchase not found: id=" + id));
        if (STATUS_RECEIVED.equals(order.getStatus())) {
            throw new IllegalArgumentException("This purchase has already been received.");
        }
        if (!STATUS_DRAFT.equals(order.getStatus())) {
            throw new IllegalArgumentException(
                    "Only a DRAFT purchase can be received; this one is " + order.getStatus() + ".");
        }

        User actor = currentUser();
        for (PurchaseOrderItem item : order.getItems()) {
            Product product = productRepository.findByIdWithLock(item.getProduct().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found"));
            int current = product.getQuantity() != null ? product.getQuantity() : 0;
            product.setQuantity(current + item.getQuantity());
            productRepository.save(product);

            stockMovementRepository.save(StockMovement.builder()
                    .product(product)
                    .type("IN")
                    .quantity(item.getQuantity())
                    .reference(order.getOrderNumber())
                    .notes("Purchase received: " + order.getOrderNumber())
                    .createdBy(actor)
                    .status(StockMovement.Status.APPROVED)
                    .approvedAt(java.time.LocalDateTime.now())
                    .approvedBy(actor)
                    .build());
        }

        order.setStatus(STATUS_RECEIVED);
        auditLogService.log(actor == null ? null : actor.getId(),
                actor == null ? "system" : actor.getUsername(),
                "PURCHASE_RECEIVED", "PURCHASE", String.valueOf(order.getId()),
                "Received " + order.getOrderNumber());
        return toDTO(purchaseOrderRepository.save(order));
    }

    private boolean hasAuthority(String authority) {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private User currentUser() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        String principal = authentication.getName();
        return userRepository.findByEmail(principal)
                .or(() -> userRepository.findByUsername(principal))
                .orElse(null);
    }

    public PurchaseOrderDTO toDTO(PurchaseOrder o) {
        PurchaseOrderDTO dto = new PurchaseOrderDTO();
        dto.setId(o.getId());
        dto.setOrderNumber(o.getOrderNumber());
        dto.setReference(o.getOrderNumber());   // frontend alias
        dto.setSupplierName(o.getSupplierName());
        dto.setTotalAmount(o.getTotalAmount());
        dto.setTotal(o.getTotalAmount());        // frontend alias
        dto.setStatus(o.getStatus());
        dto.setCreatedAt(o.getCreatedAt());

        if (o.getCreatedAt() != null) {
            dto.setDate(o.getCreatedAt().format(DATE_FMT));
        }

        // Simple payment status: assume PAID for RECEIVED orders in this version
        if ("RECEIVED".equals(o.getStatus())) {
            dto.setPaymentStatus("paid");
            dto.setPaid(o.getTotalAmount());
        } else if ("CANCELLED".equals(o.getStatus())) {
            dto.setPaymentStatus("unpaid");
            dto.setPaid(BigDecimal.ZERO);
        } else {
            dto.setPaymentStatus("unpaid");
            dto.setPaid(BigDecimal.ZERO);
        }

        // Map items
        if (o.getItems() != null) {
            dto.setItems(o.getItems().stream().map(i -> {
                PurchaseOrderItemDTO idto = new PurchaseOrderItemDTO();
                idto.setId(i.getId());
                if (i.getProduct() != null) {
                    idto.setProductId(i.getProduct().getId());
                    idto.setProductName(i.getProduct().getName());
                    idto.setProductSku(i.getProduct().getSku());
                }
                idto.setQuantity(i.getQuantity());
                idto.setUnitCost(i.getUnitCost());
                idto.setSubtotal(i.getSubtotal());
                idto.setTotal(i.getSubtotal());
                return idto;
            }).collect(Collectors.toList()));
        }

        return dto;
    }
}
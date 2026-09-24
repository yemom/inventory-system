package com.inventory.backend.service;

import com.inventory.backend.dto.*;
import com.inventory.backend.model.*;
import com.inventory.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
    private final StockMovementRepository stockMovementRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Transactional(readOnly = true)
    public Page<PurchaseOrderDTO> listPurchases(Pageable pageable) {
        return purchaseOrderRepository.findAll(pageable).map(this::toDTO);
    }

    @Transactional
    public PurchaseOrderDTO createPurchase(CreatePurchaseOrderRequest req) {
        // ── 1. Build order shell ──────────────────────────────────────────────
        PurchaseOrder order = new PurchaseOrder();
        order.setSupplierName(req.getSupplierName());
        order.setOrderNumber("PO-" + System.currentTimeMillis());

        String status = req.getStatus() != null ? req.getStatus().toUpperCase() : "RECEIVED";
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

            // ── 3. Increase stock only when purchase is RECEIVED ──────────────
            if ("RECEIVED".equals(status)) {
                int currentStock = p.getQuantity() != null ? p.getQuantity() : 0;
                p.setQuantity(currentStock + i.getQuantity());
                productRepository.save(p);
            }
        }

        order.setTotalAmount(total);
        order.setItems(items);

        PurchaseOrder saved = purchaseOrderRepository.save(order);

        // ── 4. Record stock movements when RECEIVED ───────────────────────────
        if ("RECEIVED".equals(status)) {
            for (PurchaseOrderItem item : saved.getItems()) {
                StockMovement movement = StockMovement.builder()
                        .product(item.getProduct())
                        .type("IN")
                        .quantity(item.getQuantity())
                        .reference(saved.getOrderNumber())
                        .notes("Purchase received: " + saved.getOrderNumber())
                        .build();
                stockMovementRepository.save(movement);
            }
        }

        return toDTO(saved);
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
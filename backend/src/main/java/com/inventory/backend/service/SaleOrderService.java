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
public class SaleOrderService {

    private final SaleOrderRepository saleOrderRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Transactional(readOnly = true)
    public Page<SaleOrderDTO> listSales(Pageable pageable) {
        return saleOrderRepository.findAll(pageable).map(this::toDTO);
    }

    @Transactional
    public SaleOrderDTO createSale(CreateSaleOrderRequest req) {
        // ── 1. Build the order shell ──────────────────────────────────────────
        SaleOrder order = new SaleOrder();
        order.setCustomerName(req.getCustomerName());
        order.setOrderNumber("SO-" + System.currentTimeMillis());
        order.setDiscount(req.getDiscount() != null ? req.getDiscount() : BigDecimal.ZERO);
        order.setPaymentMethod(req.getPaymentMethod());

        // Determine payment status from request; default to PAID (cash sale)
        String paymentStatus = req.getPaymentStatus() != null ? req.getPaymentStatus() : "PAID";
        order.setStatus(paymentStatus.equals("UNPAID") ? "PENDING" : "PAID");

        BigDecimal subtotal = BigDecimal.ZERO;
        List<SaleOrderItem> items = new ArrayList<>();

        // ── 2. Validate stock and build items ─────────────────────────────────
        for (CreateSaleOrderItem i : req.getItems()) {
            Product p = productRepository.findByIdWithLock(i.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: id=" + i.getProductId()));

            int currentStock = p.getQuantity() != null ? p.getQuantity() : 0;
            if (currentStock < i.getQuantity()) {
                throw new IllegalArgumentException(
                        "Insufficient stock for '" + p.getName() + "'. " +
                        "Available: " + currentStock + ", Requested: " + i.getQuantity());
            }

            // ── 3. Deduct stock atomically ────────────────────────────────────
            p.setQuantity(currentStock - i.getQuantity());
            productRepository.save(p);

            SaleOrderItem item = new SaleOrderItem();
            item.setSaleOrder(order);
            item.setProduct(p);
            item.setQuantity(i.getQuantity());
            item.setUnitPrice(i.getUnitPrice());
            item.setSubtotal(i.getUnitPrice().multiply(new BigDecimal(i.getQuantity())));
            items.add(item);

            subtotal = subtotal.add(item.getSubtotal());
        }

        order.setTotalAmount(subtotal);
        order.setFinalAmount(subtotal.subtract(order.getDiscount()));
        order.setItems(items);

        SaleOrder saved = saleOrderRepository.save(order);

        // ── 4. Record stock movement per item ─────────────────────────────────
        for (SaleOrderItem item : saved.getItems()) {
            StockMovement movement = StockMovement.builder()
                    .product(item.getProduct())
                    .type("OUT")
                    .quantity(-item.getQuantity())
                    .reference(saved.getOrderNumber())
                    .notes("Sale: " + saved.getOrderNumber())
                    .build();
            stockMovementRepository.save(movement);
        }

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
        // derive date string for frontend
        if (o.getCreatedAt() != null) {
            dto.setDate(o.getCreatedAt().format(DATE_FMT));
        }
        // derive payment status
        dto.setPaymentStatus(derivePaymentStatus(o));
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
                idto.setDiscount(BigDecimal.ZERO);
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

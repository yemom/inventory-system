package com.inventory.backend.service;

import com.inventory.backend.dto.*;
import com.inventory.backend.model.*;
import com.inventory.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Transactional(readOnly = true)
    public Page<SaleOrderDTO> listSales(Pageable pageable) {
        return saleOrderRepository.findAll(pageable).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public SaleOrderDTO getSale(Long id) {
        SaleOrder order = saleOrderRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Sale not found: id=" + id));
        return toDTO(order);
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

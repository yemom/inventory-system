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
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<PurchaseOrderDTO> listPurchases(Pageable pageable) {
        return purchaseOrderRepository.findAll(pageable).map(this::toDTO);
    }

    @Transactional
    public PurchaseOrderDTO createPurchase(CreatePurchaseOrderRequest req) {
        PurchaseOrder order = new PurchaseOrder();
        order.setSupplierName(req.getSupplierName());
        order.setOrderNumber("PO-" + System.currentTimeMillis());
        order.setStatus(req.getStatus() != null ? req.getStatus() : "RECEIVED");

        BigDecimal total = BigDecimal.ZERO;

        List<PurchaseOrderItem> items = req.getItems().stream().map(i -> {
            Product p = productRepository.findById(i.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(order);
            item.setProduct(p);
            item.setQuantity(i.getQuantity());
            item.setUnitCost(i.getUnitCost());
            item.setSubtotal(i.getUnitCost().multiply(new BigDecimal(i.getQuantity())));
            return item;
        }).collect(Collectors.toList());

        for (PurchaseOrderItem item : items) {
            total = total.add(item.getSubtotal());
        }

        order.setTotalAmount(total);
        order.setItems(items);

        return toDTO(purchaseOrderRepository.save(order));
    }

    private PurchaseOrderDTO toDTO(PurchaseOrder o) {
        PurchaseOrderDTO dto = new PurchaseOrderDTO();
        dto.setId(o.getId());
        dto.setOrderNumber(o.getOrderNumber());
        dto.setSupplierName(o.getSupplierName());
        dto.setTotalAmount(o.getTotalAmount());
        dto.setStatus(o.getStatus());
        dto.setCreatedAt(o.getCreatedAt());
        return dto;
    }
}
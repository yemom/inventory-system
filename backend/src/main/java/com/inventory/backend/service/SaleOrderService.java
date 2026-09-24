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
public class SaleOrderService {

    private final SaleOrderRepository saleOrderRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<SaleOrderDTO> listSales(Pageable pageable) {
        return saleOrderRepository.findAll(pageable).map(this::toDTO);
    }

    @Transactional
    public SaleOrderDTO createSale(CreateSaleOrderRequest req) {
        SaleOrder order = new SaleOrder();
        order.setCustomerName(req.getCustomerName());
        order.setOrderNumber("SO-" + System.currentTimeMillis());
        order.setDiscount(req.getDiscount() != null ? req.getDiscount() : BigDecimal.ZERO);
        order.setPaymentMethod(req.getPaymentMethod());
        order.setStatus("PAID");

        BigDecimal total = BigDecimal.ZERO;

        List<SaleOrderItem> items = req.getItems().stream().map(i -> {
            Product p = productRepository.findById(i.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));
            
            // For now, don't throw exception on low stock, just reduce
            // if (p.getQuantity() < i.getQuantity()) {
            //     throw new RuntimeException("Insufficient stock for product " + p.getName());
            // }
            // p.setQuantity(p.getQuantity() - i.getQuantity());
            // productRepository.save(p);

            SaleOrderItem item = new SaleOrderItem();
            item.setSaleOrder(order);
            item.setProduct(p);
            item.setQuantity(i.getQuantity());
            item.setUnitPrice(i.getUnitPrice());
            item.setSubtotal(i.getUnitPrice().multiply(new BigDecimal(i.getQuantity())));
            return item;
        }).collect(Collectors.toList());

        for (SaleOrderItem item : items) {
            total = total.add(item.getSubtotal());
        }

        order.setTotalAmount(total);
        order.setFinalAmount(total.subtract(order.getDiscount()));
        order.setItems(items);

        return toDTO(saleOrderRepository.save(order));
    }

    private SaleOrderDTO toDTO(SaleOrder o) {
        SaleOrderDTO dto = new SaleOrderDTO();
        dto.setId(o.getId());
        dto.setOrderNumber(o.getOrderNumber());
        dto.setCustomerName(o.getCustomerName());
        dto.setTotalAmount(o.getTotalAmount());
        dto.setDiscount(o.getDiscount());
        dto.setFinalAmount(o.getFinalAmount());
        dto.setStatus(o.getStatus());
        dto.setPaymentMethod(o.getPaymentMethod());
        dto.setCreatedAt(o.getCreatedAt());
        // map items if necessary
        return dto;
    }
}

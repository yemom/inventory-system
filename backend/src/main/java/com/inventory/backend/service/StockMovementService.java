package com.inventory.backend.service;

import com.inventory.backend.dto.StockMovementDTO;
import com.inventory.backend.dto.CreateStockMovementRequest;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Transactional
public class StockMovementService {
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public Page<StockMovementDTO> listMovements(Pageable pageable) {
        return stockMovementRepository.findAll(pageable).map(this::toDTO);
    }

    public StockMovementDTO recordMovement(CreateStockMovementRequest request) {
        Product product = productRepository.findByIdWithLock(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        Warehouse warehouse = null;
        if (request.getWarehouseId() != null) {
            warehouse = warehouseRepository.findById(request.getWarehouseId())
                    .orElseThrow(() -> new IllegalArgumentException("Warehouse not found"));
        }

        String principal = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(principal)
                .or(() -> userRepository.findByUsername(principal))
                .orElse(null);

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setWarehouse(warehouse);
        movement.setType(request.getType());
        movement.setQuantity(request.getQuantity());
        movement.setReference(request.getReference());
        movement.setNotes(request.getNotes());
        movement.setCreatedBy(user);

        // ── Update product stock for ADJUSTMENT movements ──────────────────────
        String type = request.getType();
        if ("ADJUSTMENT".equals(type) || "IN".equals(type) || "OUT".equals(type)) {
            int current = product.getQuantity() != null ? product.getQuantity() : 0;
            int delta = request.getQuantity() != null ? request.getQuantity() : 0;
            // For OUT and ADJUSTMENT with negative qty, delta is already negative
            int newQty = Math.max(0, current + delta);
            product.setQuantity(newQty);
            productRepository.save(product);
        }

        return toDTO(stockMovementRepository.save(movement));
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
        return dto;
    }
}

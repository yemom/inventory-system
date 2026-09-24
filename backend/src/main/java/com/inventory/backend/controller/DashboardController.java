package com.inventory.backend.controller;

import com.inventory.backend.dto.ApiResponse;
import com.inventory.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ProductRepository productRepository;
    private final SaleOrderRepository saleOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final StockMovementRepository stockMovementRepository;

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // ── Products ─────────────────────────────────────────────────────────
        long totalProducts = productRepository.count();
        long activeProducts = productRepository.countByActiveTrue();
        long lowStockProducts = productRepository.countLowStock();
        long outOfStockProducts = productRepository.countOutOfStock();
        BigDecimal inventoryValue = productRepository.totalInventoryValue();

        stats.put("totalProducts", totalProducts);
        stats.put("activeProducts", activeProducts);
        stats.put("lowStockProducts", lowStockProducts);
        stats.put("outOfStockProducts", outOfStockProducts);
        stats.put("inventoryValue", inventoryValue != null ? inventoryValue : BigDecimal.ZERO);

        // ── Sales ─────────────────────────────────────────────────────────────
        long totalSaleOrders = saleOrderRepository.count();
        BigDecimal totalRevenue = saleOrderRepository.sumFinalAmount();
        stats.put("totalSaleOrders", totalSaleOrders);
        stats.put("totalRevenue", totalRevenue != null ? totalRevenue : BigDecimal.ZERO);

        // ── Purchases ─────────────────────────────────────────────────────────
        long totalPurchaseOrders = purchaseOrderRepository.count();
        BigDecimal totalPurchaseAmount = purchaseOrderRepository.sumTotalAmount();
        stats.put("totalPurchaseOrders", totalPurchaseOrders);
        stats.put("totalPurchaseAmount", totalPurchaseAmount != null ? totalPurchaseAmount : BigDecimal.ZERO);

        // ── Customers / Suppliers ─────────────────────────────────────────────
        stats.put("totalCustomers", customerRepository.count());
        stats.put("totalSuppliers", supplierRepository.count());

        // ── Derived financial ─────────────────────────────────────────────────
        BigDecimal revenue = totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
        BigDecimal purchases = totalPurchaseAmount != null ? totalPurchaseAmount : BigDecimal.ZERO;
        stats.put("grossProfit", revenue.subtract(purchases));

        return ResponseEntity.ok(ApiResponse.ok("Dashboard stats retrieved successfully", stats));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("status", "UP");
        h.put("service", "StockFlow Inventory API");
        return ResponseEntity.ok(h);
    }
}

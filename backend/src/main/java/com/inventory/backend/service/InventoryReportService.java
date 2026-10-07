package com.inventory.backend.service;

import com.inventory.backend.model.Product;
import com.inventory.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inventory reporting.
 *
 * <p>Split across three services methods because the three reports are not the
 * same sensitivity. A low-stock list is operational and a Supervisor needs it; an
 * inventory valuation is the business's balance sheet and a Supervisor must not
 * see it. Keeping them apart here means the permission check has something real
 * to guard, rather than one "inventory report" endpoint that either over-shares
 * or under-delivers.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryReportService {

    private final ProductRepository productRepository;

    /**
     * Low-stock and out-of-stock lines. Safe for a Supervisor: it names products
     * that need reordering and nothing about what they are worth.
     */
    public Map<String, Object> lowStockReport() {
        List<Product> all = productRepository.findAll();

        List<Map<String, Object>> lowStock = new ArrayList<>();
        List<Map<String, Object>> outOfStock = new ArrayList<>();

        for (Product p : all) {
            if (!p.isActive()) {
                continue;
            }
            int quantity = p.getQuantity() != null ? p.getQuantity() : 0;
            Integer reorder = p.getReorderLevel();
            // A product with no reorder level configured has no low-stock
            // threshold, so it is reported as in stock rather than being flagged
            // by a comparison against null.
            if (quantity <= 0) {
                outOfStock.add(row(p, quantity));
            } else if (reorder != null && quantity <= reorder) {
                lowStock.add(row(p, quantity));
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("lowStock", lowStock);
        report.put("outOfStock", outOfStock);
        report.put("lowStockCount", lowStock.size());
        report.put("outOfStockCount", outOfStock.size());
        return report;
    }

    /**
     * Inventory valuation: what the stock on hand is worth at purchase cost.
     *
     * <p>Gated by {@code INVENTORY_VALUE_VIEW}, which a Supervisor does not
     * hold. Cost rather than selling price, because retail value would let a
     * supervisor infer the margin on every line.
     */
    public Map<String, Object> inventoryValueReport() {
        List<Product> all = productRepository.findAll();

        BigDecimal totalCostValue = BigDecimal.ZERO;
        BigDecimal totalRetailValue = BigDecimal.ZERO;
        int totalUnits = 0;
        List<Map<String, Object>> lines = new ArrayList<>();

        for (Product p : all) {
            if (!p.isActive()) {
                continue;
            }
            int quantity = p.getQuantity() != null ? p.getQuantity() : 0;
            BigDecimal cost = nz(p.getPurchasePrice());
            BigDecimal retail = nz(p.getSellingPrice());

            BigDecimal costValue = cost.multiply(BigDecimal.valueOf(quantity));
            BigDecimal retailValue = retail.multiply(BigDecimal.valueOf(quantity));

            totalCostValue = totalCostValue.add(costValue);
            totalRetailValue = totalRetailValue.add(retailValue);
            totalUnits += quantity;

            Map<String, Object> line = row(p, quantity);
            line.put("purchasePrice", cost);
            line.put("sellingPrice", retail);
            line.put("costValue", costValue);
            line.put("retailValue", retailValue);
            lines.add(line);
        }

        lines.sort(Comparator.comparing(m -> String.valueOf(m.get("sku"))));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("lines", lines);
        report.put("totalUnits", totalUnits);
        report.put("totalCostValue", totalCostValue);
        report.put("totalRetailValue", totalRetailValue);
        report.put("potentialMargin", totalRetailValue.subtract(totalCostValue));
        report.put("skuCount", lines.size());
        return report;
    }

    /** Count/value summary used by the inventory overview screen. */
    public Map<String, Object> inventorySummary() {
        List<Product> all = productRepository.findAll();

        BigDecimal totalValue = BigDecimal.ZERO;
        int totalUnits = 0;
        int activeProducts = 0;
        int lowStock = 0;
        int outOfStock = 0;

        for (Product p : all) {
            if (!p.isActive()) {
                continue;
            }
            activeProducts++;
            int quantity = p.getQuantity() != null ? p.getQuantity() : 0;
            totalUnits += quantity;
            totalValue = totalValue.add(nz(p.getPurchasePrice()).multiply(BigDecimal.valueOf(quantity)));
            Integer reorder = p.getReorderLevel();
            if (quantity <= 0) {
                outOfStock++;
            } else if (reorder != null && quantity <= reorder) {
                lowStock++;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalProducts", activeProducts);
        summary.put("totalUnits", totalUnits);
        summary.put("totalValue", totalValue);
        summary.put("lowStockCount", lowStock);
        summary.put("outOfStockCount", outOfStock);
        return summary;
    }

    private Map<String, Object> row(Product p, int quantity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", p.getId());
        row.put("sku", p.getSku());
        row.put("name", p.getName());
        row.put("unit", p.getUnit());
        row.put("quantity", quantity);
        row.put("reorderLevel", p.getReorderLevel());
        if (p.getCategory() != null) {
            row.put("categoryName", p.getCategory().getName());
        }
        return row;
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
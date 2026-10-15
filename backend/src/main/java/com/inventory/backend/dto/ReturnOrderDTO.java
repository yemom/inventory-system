package com.inventory.backend.dto;

import com.inventory.backend.model.ReturnOrder;
import com.inventory.backend.model.ReturnOrderItem;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A customer or supplier return, as JSON.
 *
 * <p>The controller used to return the {@link ReturnOrder} entity itself, which
 * Jackson then walked <em>after</em> the service's transaction had closed. Every
 * association on it is lazy — customer, supplier, warehouse, the two users, and
 * the item collection — so serialising it produced
 * {@code could not initialize proxy [Customer#1] - no Session} and answered 500
 * on every request that raised, listed or approved a return.
 *
 * <p>Mapping inside the transaction turns those proxies into the handful of
 * values a reader needs. The JSON keeps the same field names, so the existing
 * frontend keeps working unchanged.
 */
@Data
@Builder
public class ReturnOrderDTO {

    private Long id;
    private String returnNumber;
    private String returnType;
    private String originalReference;
    private String partyName;

    private Long customerId;
    private String customerName;
    private Long supplierId;
    private String supplierName;
    private Long warehouseId;
    private String warehouseName;

    private BigDecimal totalAmount;
    private String status;
    private String reason;
    private String notes;
    private Boolean financialSettled;

    private List<Line> items;

    private String requestedBy;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String reviewNote;
    private LocalDateTime transactionDate;
    private LocalDateTime createdAt;

    /** One line of the return. */
    @Data
    @Builder
    public static class Line {
        private Long id;
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal unitCost;
        private BigDecimal amount;
        private BigDecimal tax;
    }

    /**
     * Must be called while the owning transaction is still open — every value
     * below is read from an association that would otherwise be a detached proxy.
     */
    public static ReturnOrderDTO from(ReturnOrder entity) {
        if (entity == null) {
            return null;
        }
        return ReturnOrderDTO.builder()
                .id(entity.getId())
                .returnNumber(entity.getReturnNumber())
                .returnType(entity.getReturnType())
                .originalReference(entity.getOriginalReference())
                .partyName(entity.getPartyName())
                .customerId(entity.getCustomer() == null ? null : entity.getCustomer().getId())
                .customerName(entity.getCustomer() == null ? null : entity.getCustomer().getName())
                .supplierId(entity.getSupplier() == null ? null : entity.getSupplier().getId())
                .supplierName(entity.getSupplier() == null ? null : entity.getSupplier().getCompanyName())
                .warehouseId(entity.getWarehouse() == null ? null : entity.getWarehouse().getId())
                .warehouseName(entity.getWarehouse() == null ? null : entity.getWarehouse().getName())
                .totalAmount(entity.getTotalAmount())
                .status(entity.getStatus())
                .reason(entity.getReason())
                .notes(entity.getNotes())
                .financialSettled(entity.getFinancialSettled())
                .items(entity.getItems() == null ? List.of()
                        : entity.getItems().stream().map(ReturnOrderDTO::line).toList())
                .requestedBy(entity.getRequestedBy() == null ? null : entity.getRequestedBy().getUsername())
                .approvedBy(entity.getApprovedBy() == null ? null : entity.getApprovedBy().getUsername())
                .approvedAt(entity.getApprovedAt())
                .reviewNote(entity.getReviewNote())
                .transactionDate(entity.getTransactionDate())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private static Line line(ReturnOrderItem item) {
        return Line.builder()
                .id(item.getId())
                .productId(item.getProduct() == null ? null : item.getProduct().getId())
                .productName(item.getProduct() == null ? null : item.getProduct().getName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .unitCost(item.getUnitCost())
                .amount(item.getAmount())
                .tax(item.getTax())
                .build();
    }
}
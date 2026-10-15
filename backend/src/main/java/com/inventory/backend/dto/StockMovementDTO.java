package com.inventory.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StockMovementDTO {
    private Long id;
    private Long productId;
    private String productName;
    private String productSku;
    private Long warehouseId;
    private String warehouseName;

    /** Receiving end of a transfer. Null for every other movement type. */
    private Long destinationWarehouseId;
    private String destinationWarehouseName;

    /**
     * The precise movement type.
     *
     * <p>SALE, PURCHASE_RECEIPT, TRANSFER_OUT, TRANSFER_IN, SALES_RETURN,
     * PURCHASE_RETURN, DAMAGE, EXPIRY, LOSS, ADJUSTMENT, STOCK_COUNT, OPENING —
     * and the older IN / OUT / TRANSFER that rows written before this
     * distinction existed still carry. The difference is what lets a receipt be
     * separated from a return and a sale from shrinkage.
     */
    private String type;

    private Integer quantity;  // signed: positive=IN, negative=OUT
    private String direction;  // "in" or "out" — convenience alias for frontend
    private String reference;

    /** Why the movement happened, in business words. */
    private String reason;

    /** Unit cost of the units moved, snapshotted at posting time. */
    private java.math.BigDecimal unitCost;

    private String notes;
    private String note;       // alias for notes
    private String createdBy;
    private LocalDateTime createdAt;
    private String date;       // formatted createdAt for frontend
    /** APPROVED, PENDING or REJECTED. Null on rows written before approval existed. */
    private String status;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String reviewNote;
}

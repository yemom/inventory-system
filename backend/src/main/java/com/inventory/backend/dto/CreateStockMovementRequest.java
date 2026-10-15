package com.inventory.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateStockMovementRequest {
    @NotNull(message = "Product ID is required")
    private Long productId;

    /**
     * Where the goods are now, or for a transfer, where they are leaving from.
     *
     * <p>Optional when the business has exactly one active warehouse. With more
     * than one it is required: stock that is not attributable to a location
     * cannot be reported per warehouse, and guessing produces two reports that
     * disagree.
     */
    private Long warehouseId;

    /** For a transfer, where the goods are going. Required when type is TRANSFER. */
    private Long destinationWarehouseId;

    @NotNull(message = "Type is required")
    private String type; // IN, OUT, TRANSFER, ADJUSTMENT, DAMAGE, EXPIRY, LOSS

    /**
     * Signed: positive receives stock, negative issues it.
     *
     * <p>For ADJUSTMENT and STOCK_COUNT this is the correction itself, which is
     * why a count that overshoots is a meaningful request rather than an error.
     */
    @NotNull(message = "Quantity is required")
    private Integer quantity;

    private String reference;

    /**
     * Why the movement happened, in business words.
     *
     * <p>Reported separately from {@code notes} because its absence on a large
     * adjustment is itself a signal the anomaly model looks for.
     */
    private String reason;

    private String notes;
}
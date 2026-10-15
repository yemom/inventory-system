package com.inventory.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The pair that opens a customer session.
 *
 * <p>Both fields are on a receipt the customer already holds,
 * which is what makes the pair a proof of ownership without a
 * password. Neither field alone is evidence, and the response
 * to a wrong pair is identical to the response to a right one
 * for a closed account.
 */
public record ChatCustomerSessionRequest(
        @NotBlank(message = "An email is required")
        @Size(max = 200, message = "An email may be at most 200 characters")
        String email,
        @NotBlank(message = "An order number is required")
        @Size(max = 50, message = "An order number may be at most 50 characters")
        String orderNumber) {
}

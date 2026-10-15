package com.inventory.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A customer's question, with the session token that scopes it.
 *
 * <p>The token travels in the body rather than a header because
 * the customer channel has no cookie or JWT infrastructure, and
 * a header would need the filter chain to know about it. The
 * token is a bearer credential: whoever holds it can ask as that
 * customer, which is why it is short-lived and stored hashed.
 */
public record ChatCustomerAskRequest(
        @NotBlank(message = "A session token is required")
        String token,
        @NotBlank(message = "A question is required")
        @Size(max = 2000, message = "A question may be at most 2000 characters")
        String question,
        Long conversationId) {
}

package com.inventory.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A question for the assistant.
 *
 * <p>The length cap is a real boundary, not a formality: the
 * question is persisted, logged and possibly sent to a provider,
 * and an unbounded field in any of those is a way to make the
 * chatbot expensive.
 */
public record ChatAskRequest(
        @NotBlank(message = "A question is required")
        @Size(max = 2000, message = "A question may be at most 2000 characters")
        String question,
        Long conversationId,
        String from,
        String to) {
}

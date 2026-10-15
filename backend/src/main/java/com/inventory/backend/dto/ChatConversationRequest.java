package com.inventory.backend.dto;

import jakarta.validation.constraints.Size;

/** Starts or retitles a conversation. */
public record ChatConversationRequest(
        @Size(max = 120, message = "A title may be at most 120 characters")
        String title) {
}

package com.aishop.assistant.web;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlannerPreviewRequest(
        @NotBlank @Size(max = 4000) String message,
        @Size(max = 2000) String conversationSummary,
        @Size(max = 6) List<@Size(max = 500) String> recentMessages
) {
}

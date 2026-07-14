package com.aishop.assistant.model;

import java.util.List;

public record PlannerInput(
        String message,
        String conversationSummary,
        List<String> recentMessages
) {
    public PlannerInput {
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
    }
}

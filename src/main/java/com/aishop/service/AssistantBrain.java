package com.aishop.service;

public record AssistantBrain(
        String answer,
        String intent,
        String summary,
        String pendingOrderDraft
) {
}

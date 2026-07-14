package com.aishop.assistant.planner;

public record PlannerModelReply(
        String text,
        String modelName,
        Integer inputTokens,
        Integer outputTokens
) {
}

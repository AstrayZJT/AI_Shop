package com.aishop.assistant.model;

public record PlannerResult(
        AssistantPlan plan,
        PlannerSource source,
        String promptVersion,
        PlannerFailureCode fallbackReason,
        String rawModelOutput,
        String modelName,
        Integer inputTokens,
        Integer outputTokens
) {
}

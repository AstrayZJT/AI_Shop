package com.aishop.assistant.model;

import java.util.List;

public record AssistantPlan(
        PlanType planType,
        List<AssistantTask> tasks,
        String summary
) {
    public AssistantPlan {
        tasks = tasks == null ? null : List.copyOf(tasks);
    }
}

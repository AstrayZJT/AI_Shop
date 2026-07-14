package com.aishop.assistant.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AssistantTask(
        String taskId,
        AssistantIntent intent,
        AssistantAction action,
        ExecutionMode executionMode,
        Map<String, Object> slots,
        List<String> missingSlots,
        List<String> dependsOn,
        List<TaskCondition> conditions,
        Double confidence,
        String reason
) {
    public AssistantTask {
        slots = slots == null ? null : Map.copyOf(new LinkedHashMap<>(slots));
        missingSlots = missingSlots == null ? null : List.copyOf(missingSlots);
        dependsOn = dependsOn == null ? null : List.copyOf(dependsOn);
        conditions = conditions == null ? null : List.copyOf(conditions);
    }
}

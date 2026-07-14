package com.aishop.assistant.model;

import java.util.List;

public record TaskCondition(
        String sourceTaskId,
        ConditionField field,
        ConditionOperator operator,
        List<String> expectedValues
) {
    public TaskCondition {
        expectedValues = expectedValues == null ? null : List.copyOf(expectedValues);
    }
}

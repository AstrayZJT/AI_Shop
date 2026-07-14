package com.aishop.assistant.tool;

import java.util.LinkedHashMap;
import java.util.Map;

public record ToolExecutionOutcome(
        Map<String, Object> data,
        String message
) {
    public ToolExecutionOutcome {
        data = data == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(data));
    }
}

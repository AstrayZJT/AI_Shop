package com.aishop.assistant.tool;

import java.util.LinkedHashMap;
import java.util.Map;

import com.aishop.assistant.model.AssistantAction;

public record TaskToolResult(
        String taskId,
        AssistantAction action,
        String toolName,
        ToolExecutionStatus status,
        Map<String, Object> arguments,
        String targetRef,
        Map<String, Object> data,
        String message
) {
    public TaskToolResult {
        arguments = arguments == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(arguments));
        data = data == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(data));
    }
}

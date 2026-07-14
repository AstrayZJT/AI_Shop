package com.aishop.assistant.tool;

import java.util.LinkedHashMap;
import java.util.Map;

import com.aishop.assistant.model.AssistantAction;

public record PreparedToolCall(
        AssistantAction action,
        String toolName,
        ToolRiskLevel riskLevel,
        Map<String, Object> arguments,
        String targetRef,
        Map<String, Object> preview
) {
    public PreparedToolCall {
        arguments = arguments == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(arguments));
        preview = preview == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(preview));
    }
}

package com.aishop.assistant.function;

import java.util.List;

import com.aishop.assistant.tool.TaskToolResult;

public record FunctionCallingPreviewResult(
        String modelText,
        List<NativeToolCallView> toolCalls,
        List<TaskToolResult> results,
        String modelName,
        Integer inputTokens,
        Integer outputTokens
) {
    public FunctionCallingPreviewResult {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        results = results == null ? List.of() : List.copyOf(results);
    }
}

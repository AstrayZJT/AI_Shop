package com.aishop.assistant.function;

import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

public record ToolCallingModelReply(
        String text,
        List<ToolExecutionRequest> toolRequests,
        String modelName,
        Integer inputTokens,
        Integer outputTokens
) {
    public ToolCallingModelReply {
        toolRequests = toolRequests == null ? List.of() : List.copyOf(toolRequests);
    }
}

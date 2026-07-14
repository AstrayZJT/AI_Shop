package com.aishop.assistant.tool;

import java.util.Map;

import dev.langchain4j.agent.tool.ToolSpecification;

public interface AssistantTool {
    ToolPolicy policy();

    ToolSpecification specification();

    PreparedToolCall prepare(ToolContext context, Map<String, Object> arguments);

    ToolExecutionOutcome execute(ToolContext context, PreparedToolCall call);
}

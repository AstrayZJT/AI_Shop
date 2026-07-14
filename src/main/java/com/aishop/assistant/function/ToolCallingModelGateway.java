package com.aishop.assistant.function;

import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;

public interface ToolCallingModelGateway {
    ToolCallingModelReply propose(String message, List<ToolSpecification> tools);
}

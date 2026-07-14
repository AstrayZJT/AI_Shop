package com.aishop.assistant.function;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.aishop.assistant.orchestration.AssistantToolOrchestrator;
import com.aishop.assistant.tool.AssistantToolRegistry;
import com.aishop.assistant.tool.TaskToolResult;
import com.aishop.assistant.tool.ToolContext;
import com.aishop.config.ShopProperties;
import com.aishop.domain.AppUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

@Service
public class NativeFunctionCallingService {

    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final int MAX_TOOL_CALLS = 5;

    private final ShopProperties properties;
    private final ToolCallingModelGateway modelGateway;
    private final AssistantToolRegistry toolRegistry;
    private final AssistantToolOrchestrator orchestrator;
    private final ObjectReader argumentReader;

    public NativeFunctionCallingService(ShopProperties properties,
                                        ToolCallingModelGateway modelGateway,
                                        AssistantToolRegistry toolRegistry,
                                        AssistantToolOrchestrator orchestrator,
                                        ObjectMapper objectMapper) {
        this.properties = properties;
        this.modelGateway = modelGateway;
        this.toolRegistry = toolRegistry;
        this.orchestrator = orchestrator;
        this.argumentReader = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readerFor(new TypeReference<Map<String, Object>>() {});
    }

    public FunctionCallingPreviewResult preview(AppUser user, String message) {
        validateAvailability(message);
        ToolCallingModelReply reply = modelGateway.propose(message, toolRegistry.specifications());
        if (reply.toolRequests().size() > MAX_TOOL_CALLS) {
            throw new IllegalArgumentException("模型一次请求的工具数量超过限制");
        }
        ToolContext context = new ToolContext(user, UUID.randomUUID().toString());
        List<NativeToolCallView> views = new ArrayList<>();
        List<TaskToolResult> results = new ArrayList<>();
        int index = 0;
        for (ToolExecutionRequest request : reply.toolRequests()) {
            String callId = request.id() == null || request.id().isBlank() ? "call-" + (++index) : request.id();
            views.add(new NativeToolCallView(callId, request.name(), request.arguments()));
            Map<String, Object> arguments = parseArguments(request.arguments());
            results.add(orchestrator.executeNativeCall(context, callId, request.name(), arguments));
        }
        return new FunctionCallingPreviewResult(
                reply.text(),
                views,
                results,
                reply.modelName(),
                reply.inputTokens(),
                reply.outputTokens());
    }

    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = argumentReader.readValue(arguments);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ex) {
            throw new IllegalArgumentException("模型工具参数不是合法 JSON", ex);
        }
    }

    private void validateAvailability(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message 不能超过 " + MAX_MESSAGE_LENGTH + " 字符");
        }
        if (!properties.ai().enabled() || properties.ai().apiKey() == null || properties.ai().apiKey().isBlank()) {
            throw new IllegalStateException("原生 Function Calling 需要配置真实模型和 API Key");
        }
    }
}

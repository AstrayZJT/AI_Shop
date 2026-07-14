package com.aishop.assistant.function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.orchestration.AssistantToolOrchestrator;
import com.aishop.assistant.tool.AssistantTool;
import com.aishop.assistant.tool.AssistantToolRegistry;
import com.aishop.assistant.tool.PreparedToolCall;
import com.aishop.assistant.tool.ToolContext;
import com.aishop.assistant.tool.ToolExecutionOutcome;
import com.aishop.assistant.tool.ToolExecutionStatus;
import com.aishop.assistant.tool.ToolPolicy;
import com.aishop.assistant.tool.ToolRiskLevel;
import com.aishop.config.ShopProperties;
import com.aishop.domain.AppUser;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

class NativeFunctionCallingServiceTest {

    @Test
    void executesModelSelectedReadOnlyTool() {
        FakeTool tool = new FakeTool("query_order", AssistantAction.QUERY_ORDER, ToolRiskLevel.READ_ONLY, true);
        NativeFunctionCallingService service = service(true, "key", tool, new ToolCallingModelReply(
                null,
                List.of(request("call-1", "query_order", "{\"orderNo\":\"ORD-12345678\"}")),
                "qwen-test",
                10,
                5));

        FunctionCallingPreviewResult result = service.preview(user(), "查询订单 ORD-12345678");

        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.results().getFirst().status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
        assertThat(tool.executeCount).hasValue(1);
    }

    @Test
    void onlyPreparesModelSelectedHighRiskTool() {
        FakeTool tool = new FakeTool(
                "prepare_cancel_order", AssistantAction.CANCEL_ORDER, ToolRiskLevel.PREPARE_ONLY, false);
        NativeFunctionCallingService service = service(true, "key", tool, new ToolCallingModelReply(
                null,
                List.of(request("call-1", "prepare_cancel_order", "{\"orderNo\":\"ORD-12345678\"}")),
                "qwen-test",
                null,
                null));

        FunctionCallingPreviewResult result = service.preview(user(), "取消订单 ORD-12345678");

        assertThat(result.results().getFirst().status()).isEqualTo(ToolExecutionStatus.PREPARED);
        assertThat(tool.executeCount).hasValue(0);
    }

    @Test
    void reportsUnknownModelToolWithoutExecutingAnything() {
        FakeTool registered = new FakeTool("query_order", AssistantAction.QUERY_ORDER, ToolRiskLevel.READ_ONLY, true);
        NativeFunctionCallingService service = service(true, "key", registered, new ToolCallingModelReply(
                null,
                List.of(request("call-1", "delete_database", "{}")),
                "qwen-test",
                null,
                null));

        FunctionCallingPreviewResult result = service.preview(user(), "delete everything");

        assertThat(result.results().getFirst().status()).isEqualTo(ToolExecutionStatus.NOT_SUPPORTED);
        assertThat(registered.executeCount).hasValue(0);
    }

    @Test
    void rejectsInvalidToolArgumentsJson() {
        FakeTool tool = new FakeTool("query_order", AssistantAction.QUERY_ORDER, ToolRiskLevel.READ_ONLY, true);
        NativeFunctionCallingService service = service(true, "key", tool, new ToolCallingModelReply(
                null,
                List.of(request("call-1", "query_order", "not-json")),
                "qwen-test",
                null,
                null));

        assertThatThrownBy(() -> service.preview(user(), "查询订单"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不是合法 JSON");
    }

    @Test
    void requiresRealModelConfiguration() {
        FakeTool tool = new FakeTool("query_order", AssistantAction.QUERY_ORDER, ToolRiskLevel.READ_ONLY, true);
        NativeFunctionCallingService service = service(false, null, tool, new ToolCallingModelReply(
                null, List.of(), null, null, null));

        assertThatThrownBy(() -> service.preview(user(), "查询订单"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("需要配置真实模型");
    }

    private NativeFunctionCallingService service(boolean enabled,
                                                 String apiKey,
                                                 AssistantTool tool,
                                                 ToolCallingModelReply reply) {
        var properties = new ShopProperties(
                new ShopProperties.Ai(
                        enabled,
                        "https://example.test/v1",
                        apiKey,
                        "qwen-test",
                        "embedding-test",
                        false,
                        false,
                        Duration.ofSeconds(5),
                        0,
                        1200,
                        0.0),
                new ShopProperties.Rag(4));
        AssistantToolRegistry registry = new AssistantToolRegistry(List.of(tool));
        AssistantToolOrchestrator orchestrator = new AssistantToolOrchestrator(registry);
        ToolCallingModelGateway gateway = (message, tools) -> reply;
        return new NativeFunctionCallingService(
                properties, gateway, registry, orchestrator, new ObjectMapper());
    }

    private ToolExecutionRequest request(String id, String name, String arguments) {
        return ToolExecutionRequest.builder().id(id).name(name).arguments(arguments).build();
    }

    private AppUser user() {
        AppUser user = new AppUser();
        user.setId(1L);
        return user;
    }

    private static final class FakeTool implements AssistantTool {
        private final ToolPolicy policy;
        private final ToolSpecification specification;
        private final AtomicInteger executeCount = new AtomicInteger();

        private FakeTool(String name, AssistantAction action, ToolRiskLevel risk, boolean autoExecutable) {
            this.policy = new ToolPolicy(name, "test tool", action, risk, autoExecutable);
            this.specification = ToolSpecification.builder()
                    .name(name)
                    .description("test tool")
                    .parameters(JsonObjectSchema.builder().addStringProperty("orderNo").additionalProperties(false).build())
                    .build();
        }

        @Override
        public ToolPolicy policy() {
            return policy;
        }

        @Override
        public ToolSpecification specification() {
            return specification;
        }

        @Override
        public PreparedToolCall prepare(ToolContext context, Map<String, Object> arguments) {
            return new PreparedToolCall(
                    policy.action(), policy.name(), policy.riskLevel(), arguments, "target", Map.of("prepared", true));
        }

        @Override
        public ToolExecutionOutcome execute(ToolContext context, PreparedToolCall call) {
            executeCount.incrementAndGet();
            return new ToolExecutionOutcome(Map.of("ok", true), "ok");
        }
    }
}

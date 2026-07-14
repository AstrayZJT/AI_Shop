package com.aishop.assistant.function;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aishop.config.ShopProperties;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

class LangChain4jToolCallingModelGatewayTest {

    @Test
    void sendsToolSpecificationsAndReturnsNativeRequests() {
        CapturingModel model = new CapturingModel();
        var gateway = new LangChain4jToolCallingModelGateway(model, properties());
        ToolSpecification specification = ToolSpecification.builder()
                .name("query_order")
                .description("query order")
                .parameters(JsonObjectSchema.builder().addStringProperty("orderNo").required("orderNo").build())
                .build();

        ToolCallingModelReply reply = gateway.propose("查询订单", List.of(specification));

        assertThat(model.request.messages().get(0)).isInstanceOf(SystemMessage.class);
        assertThat(model.request.messages().get(1)).isInstanceOf(UserMessage.class);
        assertThat(model.request.toolChoice()).isEqualTo(ToolChoice.AUTO);
        assertThat(model.request.toolSpecifications()).containsExactly(specification);
        assertThat(reply.toolRequests()).extracting(ToolExecutionRequest::name).containsExactly("query_order");
        assertThat(reply.modelName()).isEqualTo("qwen-tool-test");
        assertThat(reply.inputTokens()).isEqualTo(20);
        assertThat(reply.outputTokens()).isEqualTo(10);
    }

    private ShopProperties properties() {
        return new ShopProperties(
                new ShopProperties.Ai(
                        true,
                        "https://example.test/v1",
                        "key",
                        "qwen-tool-test",
                        "embedding-test",
                        false,
                        false,
                        Duration.ofSeconds(5),
                        0,
                        1200,
                        0.0),
                new ShopProperties.Rag(4));
    }

    private static final class CapturingModel implements ChatModel {
        private ChatRequest request;

        @Override
        public ChatResponse doChat(ChatRequest request) {
            this.request = request;
            ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                    .id("call-1")
                    .name("query_order")
                    .arguments("{\"orderNo\":\"ORD-12345678\"}")
                    .build();
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(toolRequest))
                    .modelName("qwen-tool-test")
                    .tokenUsage(new TokenUsage(20, 10))
                    .build();
        }
    }
}

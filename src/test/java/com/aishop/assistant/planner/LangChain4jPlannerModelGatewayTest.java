package com.aishop.assistant.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.aishop.assistant.model.PlannerFailureCode;
import com.aishop.config.ShopProperties;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

class LangChain4jPlannerModelGatewayTest {

    @Test
    void sendsRoleSeparatedJsonRequestThroughLangChain4j() {
        CapturingChatModel chatModel = new CapturingChatModel();
        var gateway = new LangChain4jPlannerModelGateway(chatModel, properties());

        PlannerModelReply reply = gateway.generatePlan("system prompt", "user payload");

        assertThat(chatModel.request.messages()).hasSize(2);
        assertThat(chatModel.request.messages().get(0)).isInstanceOf(SystemMessage.class);
        assertThat(chatModel.request.messages().get(1)).isInstanceOf(UserMessage.class);
        assertThat(chatModel.request.temperature()).isEqualTo(0.0);
        assertThat(chatModel.request.maxOutputTokens()).isEqualTo(1200);
        assertThat(chatModel.request.responseFormat()).isEqualTo(ResponseFormat.JSON);
        assertThat(reply.text()).isEqualTo("{\"planType\":\"SINGLE_TASK\"}");
        assertThat(reply.modelName()).isEqualTo("qwen-response");
        assertThat(reply.inputTokens()).isEqualTo(12);
        assertThat(reply.outputTokens()).isEqualTo(8);
    }

    @Test
    void wrapsProviderFailureAsPlannerFailure() {
        ChatModel failingModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                throw new RuntimeException("provider timeout");
            }
        };
        var gateway = new LangChain4jPlannerModelGateway(failingModel, properties());

        assertThatThrownBy(() -> gateway.generatePlan("system", "user"))
                .isInstanceOfSatisfying(PlannerException.class,
                        ex -> assertThat(ex.code()).isEqualTo(PlannerFailureCode.MODEL_CALL_FAILED));
    }

    private ShopProperties properties() {
        return new ShopProperties(
                new ShopProperties.Ai(
                        true,
                        "https://example.test/v1",
                        "key",
                        "qwen-configured",
                        "embedding-test",
                        false,
                        false,
                        Duration.ofSeconds(5),
                        0,
                        1200,
                        0.0),
                new ShopProperties.Rag(4));
    }

    private static final class CapturingChatModel implements ChatModel {
        private ChatRequest request;

        @Override
        public ChatResponse doChat(ChatRequest request) {
            this.request = request;
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("{\"planType\":\"SINGLE_TASK\"}"))
                    .modelName("qwen-response")
                    .tokenUsage(new TokenUsage(12, 8))
                    .build();
        }
    }
}

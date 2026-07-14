package com.aishop.assistant.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

class LangChain4jRagAnswerModelGatewayTest {

    @Test
    void sendsJsonRequestWithSeparatedMessages() {
        CapturingModel model = new CapturingModel();
        var gateway = new LangChain4jRagAnswerModelGateway(model, RagTestFixtures.properties(true));

        RagAnswerModelReply reply = gateway.answer("trusted system", "untrusted payload");

        assertThat(model.request.messages().get(0)).isInstanceOf(SystemMessage.class);
        assertThat(model.request.messages().get(1)).isInstanceOf(UserMessage.class);
        assertThat(model.request.responseFormat()).isEqualTo(ResponseFormat.JSON);
        assertThat(model.request.temperature()).isEqualTo(0.0);
        assertThat(reply.modelName()).isEqualTo("qwen-rag-response");
        assertThat(reply.inputTokens()).isEqualTo(30);
        assertThat(reply.outputTokens()).isEqualTo(12);
    }

    private static final class CapturingModel implements ChatModel {
        private ChatRequest request;

        @Override
        public ChatResponse doChat(ChatRequest request) {
            this.request = request;
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("{\"answer\":\"规则\",\"usedChunkIds\":[1],\"sufficient\":true}"))
                    .modelName("qwen-rag-response")
                    .tokenUsage(new TokenUsage(30, 12))
                    .build();
        }
    }
}

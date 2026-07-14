package com.aishop.assistant.rag;

import org.springframework.stereotype.Component;

import com.aishop.config.ShopProperties;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;

@Component
public class LangChain4jRagAnswerModelGateway implements RagAnswerModelGateway {

    private final ChatModel chatModel;
    private final ShopProperties properties;

    public LangChain4jRagAnswerModelGateway(ChatModel chatModel, ShopProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    @Override
    public RagAnswerModelReply answer(String systemPrompt, String userPrompt) {
        ChatRequest request = ChatRequest.builder()
                .messages(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt))
                .responseFormat(ResponseFormat.JSON)
                .temperature(0.0)
                .maxOutputTokens(Math.min(1000, properties.ai().plannerMaxOutputTokens()))
                .build();
        var response = chatModel.chat(request);
        var tokenUsage = response.tokenUsage();
        String modelName = response.modelName();
        if (modelName == null || modelName.isBlank()) {
            modelName = properties.ai().modelName();
        }
        return new RagAnswerModelReply(
                response.aiMessage() == null ? null : response.aiMessage().text(),
                modelName,
                tokenUsage == null ? null : tokenUsage.inputTokenCount(),
                tokenUsage == null ? null : tokenUsage.outputTokenCount());
    }
}

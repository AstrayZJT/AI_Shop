package com.aishop.assistant.planner;

import org.springframework.stereotype.Component;

import com.aishop.assistant.model.PlannerFailureCode;
import com.aishop.config.ShopProperties;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;

@Component
public class LangChain4jPlannerModelGateway implements PlannerModelGateway {

    private final ChatModel chatModel;
    private final ShopProperties properties;

    public LangChain4jPlannerModelGateway(ChatModel chatModel, ShopProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    @Override
    public PlannerModelReply generatePlan(String systemPrompt, String userPrompt) {
        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt))
                    .temperature(properties.ai().plannerTemperature())
                    .maxOutputTokens(properties.ai().plannerMaxOutputTokens())
                    .responseFormat(ResponseFormat.JSON)
                    .build();
            var response = chatModel.chat(request);
            var tokenUsage = response.tokenUsage();
            String modelName = response.modelName();
            if (modelName == null || modelName.isBlank()) {
                modelName = properties.ai().modelName();
            }
            return new PlannerModelReply(
                    response.aiMessage() == null ? null : response.aiMessage().text(),
                    modelName,
                    tokenUsage == null ? null : tokenUsage.inputTokenCount(),
                    tokenUsage == null ? null : tokenUsage.outputTokenCount());
        } catch (RuntimeException ex) {
            throw new PlannerException(
                    PlannerFailureCode.MODEL_CALL_FAILED,
                    "Planner 模型调用失败",
                    ex);
        }
    }
}

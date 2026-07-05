package com.aishop.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

@Configuration
public class LocalChatModelConfig {

    @Bean
    @ConditionalOnMissingBean(ChatModel.class)
    public ChatModel chatModel() {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                List<ChatMessage> messages = request.messages();
                String userText = messages.stream()
                        .filter(UserMessage.class::isInstance)
                        .map(UserMessage.class::cast)
                        .reduce((first, second) -> second)
                        .map(UserMessage::singleText)
                        .orElse("你好");
                String systemText = messages.stream()
                        .filter(SystemMessage.class::isInstance)
                        .map(SystemMessage.class::cast)
                        .reduce((first, second) -> second)
                        .map(SystemMessage::text)
                        .orElse("");
                var answer = "我收到了: " + userText + (systemText.isBlank() ? "" : " | " + systemText.substring(0, Math.min(120, systemText.length())));
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(answer))
                        .build();
            }
        };
    }
}

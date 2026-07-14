package com.aishop.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

@Configuration
public class AiModelConfig {

    @Bean
    @ConditionalOnProperty(prefix = "shop.ai", name = "enabled", havingValue = "true")
    ChatModel chatModel(ShopProperties properties) {
        var ai = properties.ai();
        return OpenAiChatModel.builder()
                .baseUrl(ai.baseUrl())
                .apiKey(ai.apiKey())
                .modelName(ai.modelName())
                .timeout(ai.timeout())
                .maxRetries(ai.maxRetries())
                .logRequests(ai.logRequests())
                .logResponses(ai.logResponses())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "shop.ai", name = "enabled", havingValue = "true")
    EmbeddingModel remoteEmbeddingModel(ShopProperties properties) {
        var ai = properties.ai();
        return OpenAiEmbeddingModel.builder()
                .baseUrl(ai.baseUrl())
                .apiKey(ai.apiKey())
                .modelName(ai.embeddingModelName())
                .timeout(ai.timeout())
                .maxRetries(ai.maxRetries())
                .logRequests(ai.logRequests())
                .logResponses(ai.logResponses())
                .build();
    }
}

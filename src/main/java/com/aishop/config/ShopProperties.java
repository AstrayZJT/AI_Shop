package com.aishop.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop")
public record ShopProperties(Ai ai, Rag rag) {

    public record Ai(boolean enabled,
                     String baseUrl,
                     String apiKey,
                     String modelName,
                     String embeddingModelName,
                     boolean logRequests,
                     boolean logResponses,
                     Duration timeout,
                     Integer maxRetries,
                     Integer plannerMaxOutputTokens,
                     Double plannerTemperature) {
        public Ai {
            if (modelName == null || modelName.isBlank()) {
                modelName = "gpt-4o-mini";
            }
            if (embeddingModelName == null || embeddingModelName.isBlank()) {
                embeddingModelName = "text-embedding-3-small";
            }
            if (timeout == null || timeout.isNegative() || timeout.isZero()) {
                timeout = Duration.ofSeconds(30);
            }
            if (maxRetries == null || maxRetries < 0) {
                maxRetries = 1;
            }
            if (plannerMaxOutputTokens == null || plannerMaxOutputTokens <= 0) {
                plannerMaxOutputTokens = 1800;
            }
            if (plannerTemperature == null || plannerTemperature < 0 || plannerTemperature > 2) {
                plannerTemperature = 0.0;
            }
        }
    }

    public record Rag(int topK) {
        public Rag {
            if (topK <= 0) {
                topK = 4;
            }
        }
    }
}

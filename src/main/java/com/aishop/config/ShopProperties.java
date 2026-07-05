package com.aishop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop")
public record ShopProperties(Ai ai, Rag rag) {

    public record Ai(boolean enabled,
                     String baseUrl,
                     String apiKey,
                     String modelName,
                     String embeddingModelName,
                     boolean logRequests,
                     boolean logResponses) {
        public Ai {
            if (modelName == null || modelName.isBlank()) {
                modelName = "gpt-4o-mini";
            }
            if (embeddingModelName == null || embeddingModelName.isBlank()) {
                embeddingModelName = "text-embedding-3-small";
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

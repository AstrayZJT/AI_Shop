package com.aishop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant.context")
public record AssistantContextProperties(
        int maxCharacters,
        int maxCurrentMessageCharacters,
        int maxSummaryCharacters,
        int maxRecentMessages,
        int maxMessageCharacters,
        int maxOrderFacts) {

    public AssistantContextProperties {
        if (maxCharacters <= 0) {
            maxCharacters = 8_000;
        }
        if (maxCurrentMessageCharacters <= 0) {
            maxCurrentMessageCharacters = 4_000;
        }
        if (maxSummaryCharacters <= 0) {
            maxSummaryCharacters = 1_200;
        }
        if (maxRecentMessages <= 0) {
            maxRecentMessages = 6;
        }
        if (maxMessageCharacters <= 0) {
            maxMessageCharacters = 800;
        }
        if (maxOrderFacts <= 0) {
            maxOrderFacts = 3;
        }
        if (maxCurrentMessageCharacters > maxCharacters) {
            maxCurrentMessageCharacters = maxCharacters;
        }
    }
}

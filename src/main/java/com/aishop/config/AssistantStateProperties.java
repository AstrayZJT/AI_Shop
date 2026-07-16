package com.aishop.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant.state")
public record AssistantStateProperties(
        Duration inputTtl,
        Duration confirmationTtl) {

    public AssistantStateProperties {
        if (inputTtl == null || inputTtl.isNegative() || inputTtl.isZero()) {
            inputTtl = Duration.ofMinutes(30);
        }
        if (confirmationTtl == null || confirmationTtl.isNegative() || confirmationTtl.isZero()) {
            confirmationTtl = Duration.ofMinutes(10);
        }
    }
}

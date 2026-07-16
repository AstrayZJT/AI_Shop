package com.aishop.assistant.context;

import java.util.List;

import com.aishop.assistant.model.PlannerInput;

public record AssistantContext(
        String currentMessage,
        String conversationSummary,
        List<ConversationMessage> recentMessages,
        List<AuthoritativeOrderFact> authoritativeOrders,
        String unfinishedPlanSummary,
        String resolvedOrderNo,
        int estimatedCharacters,
        int maxCharacters,
        boolean truncated) {

    public AssistantContext {
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
        authoritativeOrders = authoritativeOrders == null ? List.of() : List.copyOf(authoritativeOrders);
    }

    public PlannerInput toPlannerInput() {
        List<String> messages = recentMessages.stream()
                .map(message -> message.role() + ": " + message.content())
                .toList();
        return new PlannerInput(currentMessage, conversationSummary, messages);
    }
}

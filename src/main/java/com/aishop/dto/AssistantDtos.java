package com.aishop.dto;

import java.util.List;

public final class AssistantDtos {
    private AssistantDtos() {
    }

    public record ChatRequest(Long sessionId, String message, String threadId) {}
    public record ChatResponse(Long sessionId, String answer, String intent, String threadId, List<String> sources, String pendingOrderDraft) {}
    public record SessionResponse(Long id, String title, String summary, String lastIntent) {}
    public record MessageResponse(String role, String content) {}
    public record CreateSessionResponse(Long id, String title, String summary, String lastIntent) {}
}

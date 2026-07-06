package com.aishop.dto;

import java.time.Instant;
import java.util.List;

public final class AssistantDtos {
    private AssistantDtos() {
    }

    public record ChatRequest(Long sessionId, String message, String threadId) {}
    public record ChatResponse(Long sessionId, String answer, String intent, String threadId, List<String> sources, String pendingOrderDraft) {}
    public record SessionResponse(Long id, String title, String summary, String lastIntent, String serviceStatus) {}
    public record MessageResponse(String role, String content, Instant createdAt) {}
    public record CreateSessionResponse(Long id, String title, String summary, String lastIntent, String serviceStatus) {}
    public record EscalateSessionRequest(String note) {}
}

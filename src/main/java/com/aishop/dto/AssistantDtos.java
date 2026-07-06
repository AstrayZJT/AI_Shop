package com.aishop.dto;

import java.time.Instant;
import java.util.List;

public final class AssistantDtos {
    private AssistantDtos() {
    }

    public record ChatRequest(Long sessionId, String message, String threadId) {}
    public record KnowledgeSourceResponse(Long chunkId, Long documentId, String title, String chunkText) {}
    public record SuggestedActionResponse(String key, String label, String prompt, String kind) {}
    public record ChatResponse(Long sessionId,
                               String answer,
                               String intent,
                               String threadId,
                               List<KnowledgeSourceResponse> sources,
                               String pendingOrderDraft,
                               List<SuggestedActionResponse> suggestedActions) {}
    public record SessionResponse(Long id,
                                  String title,
                                  String summary,
                                  String lastIntent,
                                  String serviceStatus,
                                  long unreadSupportCount,
                                  String supportAgentDisplayName) {}
    public record MessageResponse(String role, String content, Instant createdAt) {}
    public record CreateSessionResponse(Long id,
                                        String title,
                                        String summary,
                                        String lastIntent,
                                        String serviceStatus,
                                        long unreadSupportCount,
                                        String supportAgentDisplayName) {}
    public record RuntimeHealthResponse(boolean aiEnabled,
                                        String mode,
                                        String provider,
                                        boolean requestReady,
                                        boolean apiKeyConfigured,
                                        String baseUrl,
                                        String chatModelName,
                                        String embeddingModelName,
                                        String chatModelClass,
                                        String embeddingModelClass,
                                        String vectorStoreType,
                                        boolean vectorStorePersistent,
                                        String vectorTable,
                                        String knowledgePath,
                                        int ragTopK,
                                        long knowledgeDocumentCount,
                                        long knowledgeChunkCount,
                                        long indexedSegmentCount,
                                        List<String> warnings) {}
    public record EscalateSessionRequest(String note) {}
}

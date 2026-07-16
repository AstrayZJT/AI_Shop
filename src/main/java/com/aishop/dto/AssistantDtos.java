package com.aishop.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AssistantDtos {
    private AssistantDtos() {
    }

    public record ChatRequest(Long sessionId, String message, String threadId) {}
    public record KnowledgeSourceResponse(Long chunkId,
                                          Long documentId,
                                          String title,
                                          String chunkText,
                                          String matchMode,
                                          Double score,
                                          String matchedTerms,
                                          boolean indexed) {}
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
                                        int ragChunkSize,
                                        int ragChunkOverlap,
                                        double ragMinVectorScore,
                                        double ragMinFinalScore,
                                        int ragMaxContextCharacters,
                                        long knowledgeDocumentCount,
                                        long knowledgeChunkCount,
                                        long indexedSegmentCount,
                                        List<String> warnings) {}
    public record EscalateSessionRequest(String note) {}

    public record ConfirmPendingActionRequest(
            @NotBlank
            @Size(min = 8, max = 64)
            @Pattern(regexp = "[A-Za-z0-9._:-]+")
            String clientRequestId) {}

    public record PendingActionResponse(Long id,
                                        Long sessionId,
                                        Long planRunId,
                                        String taskId,
                                        String action,
                                        String status,
                                        String targetRef,
                                        Map<String, Object> preview,
                                        Instant expiresAt,
                                        Instant confirmedAt,
                                        Instant executedAt,
                                        Instant rejectedAt,
                                        String clientRequestId,
                                        String resultMessage,
                                        Instant createdAt) {}

    public record PendingActionOperationResponse(PendingActionResponse pendingAction,
                                                 Long planRunId,
                                                 String runStatus,
                                                 boolean idempotentReplay) {}
}

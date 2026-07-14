package com.aishop.assistant.rag;

import java.util.List;

import com.aishop.dto.KnowledgeDtos.SearchResponse;

public record RagAnswerResult(
        String answer,
        RagAnswerMode mode,
        boolean grounded,
        List<RagCitation> citations,
        List<SearchResponse> retrievalHits,
        int retrievalCount,
        boolean contextTruncated,
        String promptVersion,
        String fallbackReason,
        String modelName,
        Integer inputTokens,
        Integer outputTokens
) {
    public RagAnswerResult {
        citations = citations == null ? List.of() : List.copyOf(citations);
        retrievalHits = retrievalHits == null ? List.of() : List.copyOf(retrievalHits);
    }
}

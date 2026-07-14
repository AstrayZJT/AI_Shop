package com.aishop.assistant.rag;

import java.util.List;

import com.aishop.dto.KnowledgeDtos.SearchResponse;

public record KnowledgeRetrievalResult(
        String query,
        List<SearchResponse> hits,
        String context,
        List<Long> contextChunkIds,
        boolean contextTruncated
) {
    public KnowledgeRetrievalResult {
        hits = hits == null ? List.of() : List.copyOf(hits);
        contextChunkIds = contextChunkIds == null ? List.of() : List.copyOf(contextChunkIds);
        context = context == null ? "" : context;
    }

    public boolean hasReliableEvidence() {
        return !contextChunkIds.isEmpty();
    }
}

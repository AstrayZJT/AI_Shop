package com.aishop.dto;

public final class KnowledgeDtos {
    private KnowledgeDtos() {
    }

    public record ImportRequest(String title, String docType, String content) {}
    public record SearchResponse(Long id,
                                 Long documentId,
                                 String title,
                                 String chunkText,
                                 String matchMode,
                                 Double score,
                                 String matchedTerms,
                                 boolean indexed,
                                 int embeddingDimensions) {}
}

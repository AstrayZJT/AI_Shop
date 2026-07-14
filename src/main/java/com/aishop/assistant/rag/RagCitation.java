package com.aishop.assistant.rag;

public record RagCitation(
        Long chunkId,
        Long documentId,
        String title,
        String docType,
        Integer chunkIndex,
        Integer startOffset,
        Integer endOffset,
        String quote,
        Double score
) {
}

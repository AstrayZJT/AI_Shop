package com.aishop.service;

import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;

public interface EmbeddingStoreFacade {

    default void add(Embedding embedding, TextSegment segment) {
        upsert(java.util.UUID.randomUUID().toString(), embedding, segment);
    }

    void upsert(String id, Embedding embedding, TextSegment segment);

    EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request);

    List<TextSegment> allSegments();

    void removeAll();
}

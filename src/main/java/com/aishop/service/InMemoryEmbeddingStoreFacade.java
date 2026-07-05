package com.aishop.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;

public class InMemoryEmbeddingStoreFacade implements EmbeddingStoreFacade {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public void upsert(String id, Embedding embedding, TextSegment segment) {
        entries.put(id, new Entry(embedding, segment));
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        var query = request.queryEmbedding();
        var matches = entries.values().stream()
                .map(entry -> new EmbeddingMatch<>(
                        similarity(query.vector(), entry.embedding.vector()),
                        null,
                        entry.embedding,
                        entry.segment))
                .filter(match -> match.score() >= request.minScore())
                .sorted(Comparator.comparing(EmbeddingMatch<TextSegment>::score).reversed())
                .limit(request.maxResults())
                .toList();
        return new EmbeddingSearchResult<>(matches);
    }

    @Override
    public List<TextSegment> allSegments() {
        return entries.values().stream().map(entry -> entry.segment).toList();
    }

    @Override
    public void removeAll() {
        entries.clear();
    }

    private double similarity(float[] left, float[] right) {
        int len = Math.min(left.length, right.length);
        if (len == 0) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < len; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        double denominator = Math.sqrt(leftNorm) * Math.sqrt(rightNorm);
        return denominator == 0.0 ? 0.0 : dot / denominator;
    }

    private record Entry(Embedding embedding, TextSegment segment) {
    }
}

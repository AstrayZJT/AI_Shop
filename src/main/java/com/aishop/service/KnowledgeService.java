package com.aishop.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.config.ShopProperties;
import com.aishop.domain.KnowledgeChunk;
import com.aishop.domain.KnowledgeDocument;
import com.aishop.dto.KnowledgeDtos.ImportRequest;
import com.aishop.dto.KnowledgeDtos.SearchResponse;
import com.aishop.repository.KnowledgeChunkRepository;
import com.aishop.repository.KnowledgeDocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;

@Service
public class KnowledgeService {

    private static final List<String> DOMAIN_HINT_TOKENS = List.of(
            "退款", "退货", "售后", "物流", "发货", "订单", "保修",
            "通勤", "耳机", "平板", "手机", "规则", "政策", "地址");

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStoreFacade embeddingStore;
    private final ShopProperties properties;
    private final ObjectMapper objectMapper;

    public KnowledgeService(KnowledgeDocumentRepository documentRepository,
                            KnowledgeChunkRepository chunkRepository,
                            EmbeddingModel embeddingModel,
                            EmbeddingStoreFacade embeddingStore,
                            ShopProperties properties,
                            ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public KnowledgeDocument importDocument(ImportRequest request) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(request.title());
        document.setDocType(request.docType());
        document.setContent(request.content());
        document = documentRepository.save(document);

        for (String chunkText : splitText(request.content())) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocument(document);
            chunk.setChunkText(chunkText);
            chunk.setEmbeddingJson("[]");
            chunk = chunkRepository.save(chunk);

            Embedding embedding = embeddingModel.embed(TextSegment.from(chunkText)).content();
            chunk.setEmbeddingJson(writeEmbedding(embedding));
            chunkRepository.save(chunk);

            embeddingStore.upsert(
                    KnowledgeIndexSynchronizer.chunkUuid(chunk.getId()),
                    embedding,
                    KnowledgeIndexSynchronizer.toSegment(chunk));
        }

        return document;
    }

    @Transactional(readOnly = true)
    public List<SearchResponse> search(String keyword) {
        String query = normalizeSearchKeyword(keyword);
        if (query == null) {
            return List.of();
        }

        Embedding queryEmbedding = embeddingModel.embed(TextSegment.from(query)).content();
        LinkedHashMap<Long, SearchCandidate> candidates = new LinkedHashMap<>();
        int targetSize = Math.max(1, properties.rag().topK());
        int textLimit = Math.max(10, targetSize * 2);

        registerTextMatches(candidates, query, query, true, textLimit);
        for (String token : extractSearchTokens(query)) {
            registerTextMatches(candidates, token, token, false, textLimit);
        }

        embeddingStore.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(textLimit)
                        .minScore(0.15)
                        .build())
                .matches().stream()
                .forEach(match -> {
                    Long chunkId = match.embedded().metadata().getLong("chunk_id");
                    if (chunkId == null) {
                        return;
                    }
                    chunkRepository.findById(chunkId).ifPresent(chunk -> candidates
                            .computeIfAbsent(chunk.getId(), ignored -> new SearchCandidate(chunk))
                            .registerVectorScore(match.score()));
                });

        if (candidates.isEmpty()) {
            return List.of();
        }

        return candidates.values().stream()
                .sorted(Comparator
                        .comparingInt(SearchCandidate::rankBucket).reversed()
                        .thenComparing(SearchCandidate::displayScore, Comparator.reverseOrder())
                        .thenComparing(SearchCandidate::matchedTermCount, Comparator.reverseOrder())
                        .thenComparing(candidate -> candidate.chunk().getId()))
                .map(this::toSearchResponse)
                .limit(targetSize)
                .toList();
    }

    private List<String> splitText(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }

        String normalized = text.replace("\r", "\n");
        String[] blocks = normalized.split("\n{2,}");
        for (String block : blocks) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() <= 400) {
                result.add(trimmed);
            } else {
                for (int i = 0; i < trimmed.length(); i += 350) {
                    result.add(trimmed.substring(i, Math.min(trimmed.length(), i + 400)));
                }
            }
        }

        if (result.isEmpty()) {
            result.add(text.substring(0, Math.min(400, text.length())));
        }
        return result;
    }

    private String writeEmbedding(Embedding embedding) {
        try {
            return objectMapper.writeValueAsString(embedding.vector());
        } catch (Exception ex) {
            throw new RuntimeException("Failed to serialize embedding", ex);
        }
    }

    private void registerTextMatches(LinkedHashMap<Long, SearchCandidate> candidates,
                                     String lookupTerm,
                                     String displayTerm,
                                     boolean exactPhrase,
                                     int maxResults) {
        String normalizedLookupTerm = normalizeSearchKeyword(lookupTerm);
        if (normalizedLookupTerm == null) {
            return;
        }
        chunkRepository.findTop10ByChunkTextContainingIgnoreCaseOrderByIdDesc(normalizedLookupTerm).stream()
                .limit(maxResults)
                .forEach(chunk -> candidates
                        .computeIfAbsent(chunk.getId(), ignored -> new SearchCandidate(chunk))
                        .registerTextHit(displayTerm, exactPhrase));
    }

    private SearchResponse toSearchResponse(SearchCandidate candidate) {
        KnowledgeChunk chunk = candidate.chunk();
        return new SearchResponse(
                chunk.getId(),
                chunk.getDocument().getId(),
                chunk.getDocument().getTitle(),
                chunk.getChunkText(),
                candidate.matchMode(),
                roundScore(candidate.displayScore()),
                candidate.matchedTermsText(),
                chunkIndexed(chunk),
                estimateEmbeddingDimensions(chunk.getEmbeddingJson()));
    }

    private List<String> extractSearchTokens(String keyword) {
        LinkedHashSet<String> tokens = Arrays.stream(keyword.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .distinct()
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        String lowered = keyword.toLowerCase(Locale.ROOT);
        for (String hint : DOMAIN_HINT_TOKENS) {
            if (lowered.contains(hint)) {
                tokens.add(hint);
            }
        }
        return List.copyOf(tokens);
    }

    private String normalizeSearchKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private boolean chunkIndexed(KnowledgeChunk chunk) {
        String embeddingJson = chunk.getEmbeddingJson();
        return embeddingJson != null
                && !embeddingJson.isBlank()
                && !"[]".equals(embeddingJson.trim());
    }

    private int estimateEmbeddingDimensions(String embeddingJson) {
        if (embeddingJson == null) {
            return 0;
        }
        String trimmed = embeddingJson.trim();
        if (trimmed.isBlank() || "[]".equals(trimmed)) {
            return 0;
        }
        String body = trimmed;
        if (body.startsWith("[")) {
            body = body.substring(1);
        }
        if (body.endsWith("]")) {
            body = body.substring(0, body.length() - 1);
        }
        if (body.isBlank()) {
            return 0;
        }
        return (int) Arrays.stream(body.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .count();
    }

    private Double roundScore(double score) {
        return Math.round(score * 1000.0) / 1000.0;
    }

    private static final class SearchCandidate {
        private final KnowledgeChunk chunk;
        private final LinkedHashSet<String> matchedTerms = new LinkedHashSet<>();
        private boolean exactPhrase;
        private Double vectorScore;

        private SearchCandidate(KnowledgeChunk chunk) {
            this.chunk = chunk;
        }

        private KnowledgeChunk chunk() {
            return chunk;
        }

        private void registerTextHit(String term, boolean exact) {
            if (term != null && !term.isBlank()) {
                matchedTerms.add(term.trim());
            }
            if (exact) {
                exactPhrase = true;
            }
        }

        private void registerVectorScore(double score) {
            vectorScore = vectorScore == null ? score : Math.max(vectorScore, score);
        }

        private boolean hasTextMatch() {
            return !matchedTerms.isEmpty();
        }

        private boolean hasVectorMatch() {
            return vectorScore != null;
        }

        private int rankBucket() {
            if (hasTextMatch() && hasVectorMatch()) {
                return 3;
            }
            if (hasTextMatch()) {
                return 2;
            }
            return 1;
        }

        private Double displayScore() {
            if (hasTextMatch() && hasVectorMatch()) {
                return Math.max(vectorScore, textScore());
            }
            if (hasVectorMatch()) {
                return vectorScore;
            }
            return textScore();
        }

        private double textScore() {
            double baseScore = exactPhrase ? 0.82 : 0.62;
            double termBonus = Math.min(0.16, matchedTerms.size() * 0.08);
            return Math.min(0.99, baseScore + termBonus);
        }

        private String matchMode() {
            if (hasTextMatch() && hasVectorMatch()) {
                return "HYBRID";
            }
            if (hasTextMatch()) {
                return "TEXT";
            }
            return "VECTOR";
        }

        private String matchedTermsText() {
            return String.join(" / ", matchedTerms);
        }

        private Integer matchedTermCount() {
            return matchedTerms.size();
        }
    }
}

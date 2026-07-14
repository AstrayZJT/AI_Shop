package com.aishop.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.assistant.rag.KnowledgeRetrievalResult;
import com.aishop.config.RagProperties;
import com.aishop.config.ShopProperties;
import com.aishop.domain.KnowledgeChunk;
import com.aishop.domain.KnowledgeDocument;
import com.aishop.dto.KnowledgeDtos.ImportRequest;
import com.aishop.dto.KnowledgeDtos.SearchResponse;
import com.aishop.repository.KnowledgeChunkRepository;
import com.aishop.repository.KnowledgeDocumentRepository;
import com.aishop.service.KnowledgeQueryAnalyzer.AnalyzedQuery;
import com.aishop.service.KnowledgeTextProcessor.ProcessedChunk;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;

@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStoreFacade embeddingStore;
    private final ShopProperties shopProperties;
    private final RagProperties ragProperties;
    private final KnowledgeTextProcessor textProcessor;
    private final KnowledgeQueryAnalyzer queryAnalyzer;
    private final ObjectMapper objectMapper;

    public KnowledgeService(KnowledgeDocumentRepository documentRepository,
                            KnowledgeChunkRepository chunkRepository,
                            EmbeddingModel embeddingModel,
                            EmbeddingStoreFacade embeddingStore,
                            ShopProperties shopProperties,
                            RagProperties ragProperties,
                            KnowledgeTextProcessor textProcessor,
                            KnowledgeQueryAnalyzer queryAnalyzer,
                            ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.shopProperties = shopProperties;
        this.ragProperties = ragProperties;
        this.textProcessor = textProcessor;
        this.queryAnalyzer = queryAnalyzer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public KnowledgeDocument importDocument(ImportRequest request) {
        validateImportRequest(request);
        var processed = textProcessor.process(request.content());
        if (documentRepository.existsByContentHash(processed.contentHash())) {
            throw new IllegalArgumentException("相同内容的知识文档已经导入");
        }

        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle(request.title().strip());
        document.setDocType(request.docType().strip());
        document.setContent(processed.originalContent());
        document.setNormalizedContent(processed.normalizedContent());
        document.setContentHash(processed.contentHash());
        document = documentRepository.save(document);

        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (ProcessedChunk source : processed.chunks()) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocument(document);
            chunk.setChunkIndex(source.index());
            chunk.setStartOffset(source.startOffset());
            chunk.setEndOffset(source.endOffset());
            chunk.setChunkText(source.text());
            chunk.setContentHash(source.contentHash());
            chunk.setEmbeddingJson("[]");
            chunks.add(chunk);
        }
        chunks = chunkRepository.saveAll(chunks);

        List<TextSegment> segments = chunks.stream()
                .map(KnowledgeIndexSynchronizer::toSegment)
                .toList();
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        if (embeddings == null || embeddings.size() != chunks.size()) {
            throw new IllegalStateException("Embedding 模型返回数量与知识分段数量不一致");
        }

        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setEmbeddingJson(writeEmbedding(embeddings.get(i)));
        }
        chunkRepository.saveAll(chunks);
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            embeddingStore.upsert(
                    KnowledgeIndexSynchronizer.chunkUuid(chunk.getId()),
                    embeddings.get(i),
                    segments.get(i));
        }
        return document;
    }

    @Transactional(readOnly = true)
    public List<SearchResponse> search(String keyword) {
        return retrieve(keyword).hits();
    }

    @Transactional(readOnly = true)
    public KnowledgeRetrievalResult retrieve(String keyword) {
        AnalyzedQuery query = queryAnalyzer.analyze(keyword);
        if (query == null) {
            return new KnowledgeRetrievalResult(null, List.of(), "", List.of(), false);
        }

        int targetSize = Math.max(1, shopProperties.rag().topK());
        int candidateLimit = Math.max(10, targetSize * ragProperties.candidateMultiplier());
        LinkedHashMap<Long, SearchCandidate> candidates = new LinkedHashMap<>();

        registerTextMatches(candidates, query.normalized(), true, candidateLimit);
        for (String term : query.terms()) {
            if (!term.equalsIgnoreCase(query.normalized())) {
                registerTextMatches(candidates, term, false, candidateLimit);
            }
        }
        registerVectorMatches(candidates, query.normalized(), candidateLimit);

        LinkedHashSet<String> seenContent = new LinkedHashSet<>();
        List<SearchResponse> hits = candidates.values().stream()
                .filter(candidate -> candidate.finalScore(query.terms().size()) >= ragProperties.minFinalScore())
                .sorted(Comparator
                        .comparingDouble((SearchCandidate candidate) -> candidate.finalScore(query.terms().size()))
                        .reversed()
                        .thenComparing(candidate -> candidate.chunk().getId()))
                .filter(candidate -> seenContent.add(candidate.deduplicationKey()))
                .limit(targetSize)
                .map(candidate -> toSearchResponse(candidate, query.terms().size()))
                .toList();

        return buildContext(query.normalized(), hits);
    }

    private void validateImportRequest(ImportRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("知识文档不能为空");
        }
        if (request.title() == null || request.title().isBlank() || request.title().length() > 128) {
            throw new IllegalArgumentException("知识文档标题不能为空且不能超过 128 字符");
        }
        if (request.docType() == null || request.docType().isBlank() || request.docType().length() > 64) {
            throw new IllegalArgumentException("知识文档类型不能为空且不能超过 64 字符");
        }
    }

    private void registerTextMatches(Map<Long, SearchCandidate> candidates,
                                     String term,
                                     boolean exactPhrase,
                                     int maxResults) {
        if (term == null || term.isBlank()) {
            return;
        }
        chunkRepository.findTop10ByChunkTextContainingIgnoreCaseOrderByIdDesc(term).stream()
                .limit(maxResults)
                .forEach(chunk -> candidates
                        .computeIfAbsent(chunk.getId(), ignored -> new SearchCandidate(chunk))
                        .registerKeyword(term, exactPhrase));
    }

    private void registerVectorMatches(Map<Long, SearchCandidate> candidates,
                                       String query,
                                       int maxResults) {
        Map<Long, Double> vectorScores = new LinkedHashMap<>();
        try {
            Embedding queryEmbedding = embeddingModel.embed(TextSegment.from(query)).content();
            var matches = embeddingStore.search(EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(maxResults)
                            .minScore(ragProperties.minVectorScore())
                            .build())
                    .matches();
            for (var match : matches) {
                Long chunkId = match.embedded().metadata().getLong("chunk_id");
                if (chunkId != null) {
                    vectorScores.merge(chunkId, match.score(), Math::max);
                }
            }
        } catch (RuntimeException ex) {
            log.warn("vector retrieval failed, continuing with keyword candidates: type={}",
                    ex.getClass().getSimpleName());
            return;
        }
        for (KnowledgeChunk chunk : chunkRepository.findAllById(vectorScores.keySet())) {
            candidates.computeIfAbsent(chunk.getId(), ignored -> new SearchCandidate(chunk))
                    .registerVector(vectorScores.get(chunk.getId()));
        }
    }

    private SearchResponse toSearchResponse(SearchCandidate candidate, int queryTermCount) {
        KnowledgeChunk chunk = candidate.chunk();
        return new SearchResponse(
                chunk.getId(),
                chunk.getDocument().getId(),
                chunk.getDocument().getTitle(),
                chunk.getChunkText(),
                candidate.matchMode(),
                roundScore(candidate.finalScore(queryTermCount)),
                candidate.matchedTermsText(),
                chunkIndexed(chunk),
                estimateEmbeddingDimensions(chunk.getEmbeddingJson()),
                chunk.getDocument().getDocType(),
                chunk.getChunkIndex(),
                chunk.getStartOffset(),
                chunk.getEndOffset(),
                candidate.hasKeywordMatch() ? roundScore(candidate.keywordScore(queryTermCount)) : null,
                candidate.hasVectorMatch() ? roundScore(candidate.vectorScore()) : null);
    }

    private KnowledgeRetrievalResult buildContext(String query, List<SearchResponse> hits) {
        int maxCharacters = ragProperties.maxContextCharacters();
        StringBuilder context = new StringBuilder();
        List<Long> includedIds = new ArrayList<>();
        boolean truncated = false;
        for (SearchResponse hit : hits) {
            String block = contextBlock(hit);
            if (context.length() + block.length() > maxCharacters) {
                truncated = true;
                continue;
            }
            context.append(block);
            includedIds.add(hit.id());
        }
        return new KnowledgeRetrievalResult(query, hits, context.toString(), includedIds, truncated);
    }

    private String contextBlock(SearchResponse hit) {
        return """
                <knowledge_chunk id="%d" document_id="%d" title="%s" doc_type="%s" start="%s" end="%s">
                %s
                </knowledge_chunk>
                """.formatted(
                hit.id(),
                hit.documentId(),
                sanitizeMetadata(hit.title()),
                sanitizeMetadata(hit.docType()),
                hit.startOffset() == null ? "unknown" : hit.startOffset(),
                hit.endOffset() == null ? "unknown" : hit.endOffset(),
                hit.chunkText());
    }

    private String sanitizeMetadata(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n<>\"]", " ").strip();
    }

    private String writeEmbedding(Embedding embedding) {
        try {
            return objectMapper.writeValueAsString(embedding.vector());
        } catch (Exception ex) {
            throw new IllegalStateException("Embedding 序列化失败", ex);
        }
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
        String body = trimmed.startsWith("[") ? trimmed.substring(1) : trimmed;
        body = body.endsWith("]") ? body.substring(0, body.length() - 1) : body;
        return body.isBlank() ? 0 : (int) Arrays.stream(body.split(","))
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

        private void registerKeyword(String term, boolean exact) {
            matchedTerms.add(term.strip());
            exactPhrase |= exact;
        }

        private void registerVector(double score) {
            vectorScore = vectorScore == null ? score : Math.max(vectorScore, score);
        }

        private boolean hasKeywordMatch() {
            return !matchedTerms.isEmpty();
        }

        private boolean hasVectorMatch() {
            return vectorScore != null;
        }

        private double keywordScore(int queryTermCount) {
            if (exactPhrase) {
                return 0.92;
            }
            double coverage = matchedTerms.size() / (double) Math.max(1, queryTermCount);
            return Math.min(0.88, 0.48 + coverage * 0.32 + Math.min(0.08, matchedTerms.size() * 0.04));
        }

        private double vectorScore() {
            return vectorScore == null ? 0 : vectorScore;
        }

        private double finalScore(int queryTermCount) {
            if (hasKeywordMatch() && hasVectorMatch()) {
                return Math.min(0.99, keywordScore(queryTermCount) * 0.45 + vectorScore() * 0.55 + 0.08);
            }
            if (hasKeywordMatch()) {
                return keywordScore(queryTermCount);
            }
            return vectorScore();
        }

        private String matchMode() {
            if (hasKeywordMatch() && hasVectorMatch()) {
                return "HYBRID";
            }
            return hasKeywordMatch() ? "KEYWORD" : "VECTOR";
        }

        private String matchedTermsText() {
            return String.join(" / ", matchedTerms);
        }

        private String deduplicationKey() {
            String hash = chunk.getContentHash();
            return hash == null || hash.isBlank()
                    ? KnowledgeTextProcessor.sha256(chunk.getChunkText().strip())
                    : hash;
        }
    }
}

package com.aishop.service;

import java.util.ArrayList;
import java.util.Arrays;
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
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        Embedding queryEmbedding = embeddingModel.embed(TextSegment.from(keyword)).content();
        LinkedHashMap<Long, KnowledgeChunk> orderedChunks = new LinkedHashMap<>();
        int targetSize = Math.max(1, properties.rag().topK());

        for (KnowledgeChunk chunk : chunkRepository.findTop10ByChunkTextContainingIgnoreCaseOrderByIdDesc(keyword.trim())) {
            orderedChunks.putIfAbsent(chunk.getId(), chunk);
            if (orderedChunks.size() >= targetSize) {
                break;
            }
        }

        if (orderedChunks.size() < targetSize) {
            for (String token : extractSearchTokens(keyword)) {
                for (KnowledgeChunk chunk : chunkRepository.findTop10ByChunkTextContainingIgnoreCaseOrderByIdDesc(token)) {
                    orderedChunks.putIfAbsent(chunk.getId(), chunk);
                    if (orderedChunks.size() >= targetSize) {
                        break;
                    }
                }
                if (orderedChunks.size() >= targetSize) {
                    break;
                }
            }
        }

        embeddingStore.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(targetSize)
                        .minScore(0.15)
                        .build())
                .matches().stream()
                .map(match -> match.embedded().metadata().getLong("chunk_id"))
                .filter(Objects::nonNull)
                .distinct()
                .forEach(chunkId -> chunkRepository.findById(chunkId)
                        .ifPresent(chunk -> orderedChunks.putIfAbsent(chunk.getId(), chunk)));

        if (orderedChunks.isEmpty()) {
            return List.of();
        }

        return orderedChunks.values().stream()
                .map(chunk -> new SearchResponse(chunk.getId(), chunk.getDocument().getTitle(), chunk.getChunkText()))
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
}

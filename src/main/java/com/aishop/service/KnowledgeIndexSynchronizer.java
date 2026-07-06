package com.aishop.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.domain.KnowledgeChunk;
import com.aishop.repository.KnowledgeChunkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;

@Component
public class KnowledgeIndexSynchronizer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexSynchronizer.class);

    private final KnowledgeChunkRepository chunkRepository;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStoreFacade embeddingStore;
    private final ObjectMapper objectMapper;

    public KnowledgeIndexSynchronizer(KnowledgeChunkRepository chunkRepository,
                                      EmbeddingModel embeddingModel,
                                      EmbeddingStoreFacade embeddingStore,
                                      ObjectMapper objectMapper) {
        this.chunkRepository = chunkRepository;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        List<KnowledgeChunk> chunks = chunkRepository.findAll();
        if (chunks.isEmpty()) {
            log.info("knowledge index sync skipped: no knowledge chunks found");
            return;
        }
        int expectedDimension = embeddingModel.dimension();

        embeddingStore.removeAll();

        List<KnowledgeChunk> updatedChunks = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            Embedding embedding = loadEmbedding(chunk, expectedDimension);
            if (embedding == null) {
                embedding = embeddingModel.embed(TextSegment.from(chunk.getChunkText())).content();
                chunk.setEmbeddingJson(objectMapper.writeValueAsString(embedding.vector()));
                updatedChunks.add(chunk);
            }

            embeddingStore.upsert(chunkUuid(chunk.getId()), embedding, toSegment(chunk));
        }

        if (!updatedChunks.isEmpty()) {
            chunkRepository.saveAll(updatedChunks);
        }

        log.info("knowledge index sync completed: indexedChunks={}, regeneratedEmbeddings={}",
                chunks.size(), updatedChunks.size());
    }

    public static String chunkUuid(Long chunkId) {
        return UUID.nameUUIDFromBytes(("knowledge-chunk-" + chunkId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static TextSegment toSegment(KnowledgeChunk chunk) {
        Metadata metadata = new Metadata()
                .put("chunk_id", chunk.getId())
                .put("document_id", chunk.getDocument().getId())
                .put("title", chunk.getDocument().getTitle())
                .put("doc_type", chunk.getDocument().getDocType());
        return TextSegment.from(chunk.getChunkText(), metadata);
    }

    private Embedding loadEmbedding(KnowledgeChunk chunk, int expectedDimension) {
        String embeddingJson = chunk.getEmbeddingJson();
        if (embeddingJson == null || embeddingJson.isBlank() || "[]".equals(embeddingJson.trim())) {
            return null;
        }
        try {
            float[] vector = objectMapper.readValue(embeddingJson, float[].class);
            if (vector.length == 0) {
                return null;
            }
            if (vector.length != expectedDimension) {
                log.info("knowledge chunk {} cached embedding dimension mismatch: cached={}, expected={}, regenerating",
                        chunk.getId(), vector.length, expectedDimension);
                return null;
            }
            return Embedding.from(vector);
        } catch (Exception ex) {
            log.warn("failed to parse cached embedding for knowledge chunk {}", chunk.getId(), ex);
            return null;
        }
    }
}

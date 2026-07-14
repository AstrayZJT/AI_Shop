package com.aishop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.aishop.config.RagProperties;
import com.aishop.config.ShopProperties;
import com.aishop.domain.KnowledgeChunk;
import com.aishop.domain.KnowledgeDocument;
import com.aishop.dto.KnowledgeDtos.ImportRequest;
import com.aishop.repository.KnowledgeChunkRepository;
import com.aishop.repository.KnowledgeDocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;

class KnowledgeServiceTest {

    @Test
    void importsCleanedChunksWithOffsetsAndBatchEmbeddings() {
        TestDependencies dependencies = dependencies(properties(200, 40, 2000));
        AtomicLong ids = new AtomicLong(100);
        when(dependencies.documentRepository.save(any(KnowledgeDocument.class))).thenAnswer(invocation -> {
            KnowledgeDocument document = invocation.getArgument(0);
            document.setId(10L);
            return document;
        });
        when(dependencies.chunkRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<KnowledgeChunk> chunks = invocation.getArgument(0);
            chunks.forEach(chunk -> {
                if (chunk.getId() == null) {
                    chunk.setId(ids.incrementAndGet());
                }
            });
            return chunks;
        });
        when(dependencies.embeddingModel.embedAll(anyList())).thenAnswer(invocation -> {
            List<TextSegment> segments = invocation.getArgument(0);
            return Response.from(segments.stream()
                    .map(segment -> Embedding.from(new float[]{1.0f, 0.5f}))
                    .toList());
        });

        String content = "退货规则。" + "甲".repeat(190) + "。退款规则。" + "乙".repeat(120);
        KnowledgeDocument document = dependencies.service.importDocument(
                new ImportRequest("售后规则", "POLICY", content));

        assertThat(document.getContent()).isEqualTo(content);
        assertThat(document.getNormalizedContent()).isNotBlank();
        assertThat(document.getContentHash()).hasSize(64);
        verify(dependencies.embeddingModel).embedAll(anyList());
        verify(dependencies.embeddingStore, atLeastOnce())
                .upsert(anyString(), any(Embedding.class), any(TextSegment.class));
    }

    @Test
    void fusesKeywordAndVectorEvidenceIntoTraceableContext() {
        TestDependencies dependencies = dependencies(properties(500, 80, 2000));
        KnowledgeChunk chunk = chunk(1L, "平台支持签收后七天无理由退货。", 0);
        Embedding embedding = Embedding.from(new float[]{1.0f, 0.0f});
        TextSegment segment = KnowledgeIndexSynchronizer.toSegment(chunk);

        when(dependencies.chunkRepository.findTop10ByChunkTextContainingIgnoreCaseOrderByIdDesc(anyString()))
                .thenAnswer(invocation -> {
                    String term = invocation.getArgument(0);
                    return chunk.getChunkText().contains(term) ? List.of(chunk) : List.of();
                });
        when(dependencies.embeddingModel.embed(any(TextSegment.class))).thenReturn(Response.from(embedding));
        when(dependencies.embeddingStore.search(any())).thenReturn(new EmbeddingSearchResult<>(List.of(
                new EmbeddingMatch<>(0.86, "chunk-1", embedding, segment))));
        when(dependencies.chunkRepository.findAllById(any())).thenReturn(List.of(chunk));

        var retrieval = dependencies.service.retrieve("七天无理由");

        assertThat(retrieval.hits()).hasSize(1);
        assertThat(retrieval.hits().get(0).matchMode()).isEqualTo("HYBRID");
        assertThat(retrieval.hits().get(0).keywordScore()).isNotNull();
        assertThat(retrieval.hits().get(0).vectorScore()).isEqualTo(0.86);
        assertThat(retrieval.contextChunkIds()).containsExactly(1L);
        assertThat(retrieval.context()).contains("knowledge_chunk id=\"1\"", "七天无理由");
    }

    @Test
    void rejectsVectorOnlyCandidateBelowFinalReliabilityThreshold() {
        TestDependencies dependencies = dependencies(properties(500, 80, 2000));
        KnowledgeChunk chunk = chunk(1L, "完全无关的内容。", 0);
        Embedding embedding = Embedding.from(new float[]{1.0f, 0.0f});
        TextSegment segment = KnowledgeIndexSynchronizer.toSegment(chunk);

        when(dependencies.chunkRepository.findTop10ByChunkTextContainingIgnoreCaseOrderByIdDesc(anyString()))
                .thenReturn(List.of());
        when(dependencies.embeddingModel.embed(any(TextSegment.class))).thenReturn(Response.from(embedding));
        when(dependencies.embeddingStore.search(any())).thenReturn(new EmbeddingSearchResult<>(List.of(
                new EmbeddingMatch<>(0.61, "chunk-1", embedding, segment))));
        when(dependencies.chunkRepository.findAllById(any())).thenReturn(List.of(chunk));

        var retrieval = dependencies.service.retrieve("发票规则");

        assertThat(retrieval.hits()).isEmpty();
        assertThat(retrieval.hasReliableEvidence()).isFalse();
    }

    @Test
    void fallsBackToKeywordRetrievalWhenVectorStoreFails() {
        TestDependencies dependencies = dependencies(properties(500, 80, 2000));
        KnowledgeChunk chunk = chunk(1L, "退款申请会在审核后处理。", 0);
        when(dependencies.chunkRepository.findTop10ByChunkTextContainingIgnoreCaseOrderByIdDesc(anyString()))
                .thenAnswer(invocation -> {
                    String term = invocation.getArgument(0);
                    return chunk.getChunkText().contains(term) ? List.of(chunk) : List.of();
                });
        when(dependencies.embeddingModel.embed(any(TextSegment.class)))
                .thenReturn(Response.from(Embedding.from(new float[]{1.0f, 0.0f})));
        when(dependencies.embeddingStore.search(any())).thenThrow(new RuntimeException("vector unavailable"));

        var retrieval = dependencies.service.retrieve("退款");

        assertThat(retrieval.hits()).hasSize(1);
        assertThat(retrieval.hits().get(0).matchMode()).isEqualTo("KEYWORD");
    }

    @Test
    void marksContextTruncatedWithoutCitingExcludedChunks() {
        TestDependencies dependencies = dependencies(properties(500, 80, 1000));
        KnowledgeChunk first = chunk(1L, "退货" + "甲".repeat(650), 0);
        KnowledgeChunk second = chunk(2L, "退货" + "乙".repeat(650), 1);
        when(dependencies.chunkRepository.findTop10ByChunkTextContainingIgnoreCaseOrderByIdDesc(anyString()))
                .thenReturn(List.of(first, second));
        when(dependencies.embeddingModel.embed(any(TextSegment.class)))
                .thenReturn(Response.from(Embedding.from(new float[]{1.0f, 0.0f})));
        when(dependencies.embeddingStore.search(any()))
                .thenReturn(new EmbeddingSearchResult<>(List.of()));

        var retrieval = dependencies.service.retrieve("退货");

        assertThat(retrieval.hits()).hasSize(2);
        assertThat(retrieval.contextTruncated()).isTrue();
        assertThat(retrieval.contextChunkIds()).containsExactly(1L);
        assertThat(retrieval.context()).contains("甲").doesNotContain("乙");
    }

    private TestDependencies dependencies(RagProperties ragProperties) {
        KnowledgeDocumentRepository documentRepository = mock(KnowledgeDocumentRepository.class);
        KnowledgeChunkRepository chunkRepository = mock(KnowledgeChunkRepository.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        EmbeddingStoreFacade embeddingStore = mock(EmbeddingStoreFacade.class);
        ObjectMapper objectMapper = new ObjectMapper();
        KnowledgeService service = new KnowledgeService(
                documentRepository,
                chunkRepository,
                embeddingModel,
                embeddingStore,
                shopProperties(),
                ragProperties,
                new KnowledgeTextProcessor(ragProperties),
                new KnowledgeQueryAnalyzer(),
                objectMapper);
        return new TestDependencies(
                service, documentRepository, chunkRepository, embeddingModel, embeddingStore);
    }

    private RagProperties properties(int chunkSize, int overlap, int maxContextCharacters) {
        return new RagProperties(
                "knowledge", chunkSize, overlap, 4, 0.60, 0.62, maxContextCharacters,
                new RagProperties.Pgvector(null, null, null, null, null, "knowledge_embeddings"));
    }

    private ShopProperties shopProperties() {
        return new ShopProperties(
                new ShopProperties.Ai(
                        false, "https://example.test/v1", "", "chat", "embedding",
                        false, false, Duration.ofSeconds(5), 0, 1200, 0.0),
                new ShopProperties.Rag(4));
    }

    private KnowledgeChunk chunk(long id, String text, int index) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(10L);
        document.setTitle("测试规则");
        document.setDocType("POLICY");
        document.setContent(text);

        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setDocument(document);
        chunk.setChunkIndex(index);
        chunk.setStartOffset(index * 100);
        chunk.setEndOffset(index * 100 + text.length());
        chunk.setChunkText(text);
        chunk.setEmbeddingJson("[1.0,0.0]");
        return chunk;
    }

    private record TestDependencies(
            KnowledgeService service,
            KnowledgeDocumentRepository documentRepository,
            KnowledgeChunkRepository chunkRepository,
            EmbeddingModel embeddingModel,
            EmbeddingStoreFacade embeddingStore
    ) {
    }
}

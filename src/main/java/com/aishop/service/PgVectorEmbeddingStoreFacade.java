package com.aishop.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.aishop.config.RagProperties;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

public class PgVectorEmbeddingStoreFacade implements EmbeddingStoreFacade {

    private final DataSource dataSource;
    private final String tableName;
    private final PgVectorEmbeddingStore store;

    public PgVectorEmbeddingStoreFacade(DataSource dataSource,
                                        EmbeddingModel embeddingModel,
                                        RagProperties ragProperties) throws SQLException {
        this.dataSource = dataSource;
        this.tableName = sanitizeTableName(ragProperties.pgvector().table());
        this.store = PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(tableName)
                .dimension(embeddingModel.dimension())
                .createTable(true)
                .dropTableFirst(false)
                .build();
    }

    @Override
    public void upsert(String id, Embedding embedding, TextSegment segment) {
        store.addAll(List.of(id), List.of(embedding), List.of(segment));
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        return store.search(request);
    }

    @Override
    public List<TextSegment> allSegments() {
        List<TextSegment> segments = new ArrayList<>();
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("select text from " + tableName + " where text is not null");
             var resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                segments.add(TextSegment.from(resultSet.getString(1)));
            }
            return segments;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to read pgvector segments", ex);
        }
    }

    @Override
    public long segmentCount() {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("select count(*) from " + tableName);
             var resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getLong(1);
            }
            return 0L;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to count pgvector segments", ex);
        }
    }

    @Override
    public void removeAll() {
        store.removeAll();
    }

    private String sanitizeTableName(String raw) {
        String candidate = raw == null || raw.isBlank() ? "knowledge_embeddings" : raw.trim();
        if (!candidate.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid pgvector table name: " + candidate);
        }
        return candidate;
    }
}

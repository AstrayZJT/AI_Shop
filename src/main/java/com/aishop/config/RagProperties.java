package com.aishop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public record RagProperties(String knowledgePath,
                            Integer chunkSize,
                            Integer chunkOverlap,
                            Integer candidateMultiplier,
                            Double minVectorScore,
                            Double minFinalScore,
                            Integer maxContextCharacters,
                            Pgvector pgvector) {

    public RagProperties {
        if (knowledgePath == null || knowledgePath.isBlank()) {
            knowledgePath = "knowledge";
        }
        if (chunkSize == null || chunkSize < 200 || chunkSize > 2000) {
            chunkSize = 500;
        }
        if (chunkOverlap == null || chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            chunkOverlap = 80;
        }
        if (candidateMultiplier == null || candidateMultiplier < 2 || candidateMultiplier > 10) {
            candidateMultiplier = 4;
        }
        if (minVectorScore == null || minVectorScore < 0 || minVectorScore > 1) {
            minVectorScore = 0.78;
        }
        if (minFinalScore == null || minFinalScore < 0 || minFinalScore > 1) {
            minFinalScore = 0.62;
        }
        if (maxContextCharacters == null
                || maxContextCharacters < chunkSize + 500
                || maxContextCharacters > 20000) {
            maxContextCharacters = 6000;
        }
        if (pgvector == null) {
            pgvector = new Pgvector(null, null, null, null, null, "knowledge_embeddings");
        }
    }

    public record Pgvector(String host,
                           Integer port,
                           String database,
                           String username,
                           String password,
                           String table) {
        public Pgvector {
            if (table == null || table.isBlank()) {
                table = "knowledge_embeddings";
            }
        }
    }
}

package com.aishop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public record RagProperties(String knowledgePath, Pgvector pgvector) {

    public RagProperties {
        if (knowledgePath == null || knowledgePath.isBlank()) {
            knowledgePath = "knowledge";
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

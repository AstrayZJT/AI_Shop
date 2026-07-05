package com.aishop.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.aishop.service.EmbeddingStoreFacade;
import com.aishop.service.InMemoryEmbeddingStoreFacade;
import com.aishop.service.PgVectorEmbeddingStoreFacade;

import dev.langchain4j.model.embedding.EmbeddingModel;

@Configuration
public class EmbeddingStoreConfig {

    @Bean
    EmbeddingStoreFacade embeddingStoreFacade(DataSource dataSource,
                                              EmbeddingModel embeddingModel,
                                              RagProperties ragProperties) throws SQLException {
        DataSource vectorDataSource = resolveVectorDataSource(dataSource, ragProperties);
        if (isPostgres(vectorDataSource)) {
            return new PgVectorEmbeddingStoreFacade(vectorDataSource, embeddingModel, ragProperties);
        }
        return new InMemoryEmbeddingStoreFacade();
    }

    private DataSource resolveVectorDataSource(DataSource fallback, RagProperties ragProperties) {
        RagProperties.Pgvector pgvector = ragProperties.pgvector();
        if (pgvector == null
                || isBlank(pgvector.host())
                || pgvector.port() == null
                || isBlank(pgvector.database())
                || isBlank(pgvector.username())) {
            return fallback;
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl("jdbc:postgresql://" + pgvector.host() + ":" + pgvector.port() + "/" + pgvector.database());
        dataSource.setUsername(pgvector.username());
        dataSource.setPassword(pgvector.password() == null ? "" : pgvector.password());
        return dataSource;
    }

    private boolean isPostgres(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            return "PostgreSQL".equalsIgnoreCase(metaData.getDatabaseProductName());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

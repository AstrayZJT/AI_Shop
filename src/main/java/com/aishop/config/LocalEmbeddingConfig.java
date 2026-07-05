package com.aishop.config;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;

@Configuration
public class LocalEmbeddingConfig {

    @Bean
    @ConditionalOnMissingBean(dev.langchain4j.model.embedding.EmbeddingModel.class)
    public DimensionAwareEmbeddingModel embeddingModel() {
        return new DimensionAwareEmbeddingModel() {
            @Override
            protected Integer knownDimension() {
                return 64;
            }

            @Override
            public Response<java.util.List<Embedding>> embedAll(java.util.List<TextSegment> segments) {
                return Response.from(segments.stream().map(segment -> embedOne(segment.text())).toList());
            }

            private Embedding embedOne(String text) {
                float[] vector = new float[64];
                Arrays.fill(vector, 0f);
                var bytes = text == null ? new byte[0] : text.toLowerCase().getBytes(StandardCharsets.UTF_8);
                for (int i = 0; i < bytes.length; i++) {
                    vector[i % vector.length] += (bytes[i] & 0xFF) / 255.0f;
                }
                var embedding = Embedding.from(vector);
                embedding.normalize();
                return embedding;
            }
        };
    }
}

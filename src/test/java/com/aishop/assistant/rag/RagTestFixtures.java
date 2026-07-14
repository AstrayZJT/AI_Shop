package com.aishop.assistant.rag;

import java.time.Duration;
import java.util.List;

import com.aishop.config.ShopProperties;
import com.aishop.dto.KnowledgeDtos.SearchResponse;

final class RagTestFixtures {

    private RagTestFixtures() {
    }

    static ShopProperties properties(boolean enabled) {
        return new ShopProperties(
                new ShopProperties.Ai(
                        enabled,
                        "https://example.test/v1",
                        enabled ? "test-key" : "",
                        "qwen-rag-test",
                        "embedding-test",
                        false,
                        false,
                        Duration.ofSeconds(5),
                        0,
                        1200,
                        0.0),
                new ShopProperties.Rag(4));
    }

    static SearchResponse hit(long id, String text) {
        return new SearchResponse(
                id,
                10L,
                "退换货规则",
                text,
                "HYBRID",
                0.91,
                "七天 / 无理由",
                true,
                1024,
                "POLICY",
                0,
                10,
                10 + text.length(),
                0.92,
                0.84);
    }

    static KnowledgeRetrievalResult retrieval(SearchResponse... hits) {
        List<SearchResponse> values = List.of(hits);
        return new KnowledgeRetrievalResult(
                "七天无理由怎么退货",
                values,
                "context",
                values.stream().map(SearchResponse::id).toList(),
                false);
    }
}

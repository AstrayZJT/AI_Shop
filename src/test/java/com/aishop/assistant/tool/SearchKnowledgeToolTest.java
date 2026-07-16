package com.aishop.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aishop.assistant.tool.tools.SearchKnowledgeTool;
import com.aishop.assistant.rag.KnowledgeRetrievalResult;
import com.aishop.dto.KnowledgeDtos.SearchResponse;
import com.aishop.service.KnowledgeService;

class SearchKnowledgeToolTest {

    @Test
    void returnsKnowledgeSourcesWithCitations() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        SearchResponse hit = new SearchResponse(
                1L, 2L, "退款规则", "七天内可以申请", "HYBRID", 0.92, "七天", true, 1024,
                "POLICY", 0, 0, 8, 0.9, 0.88);
        when(knowledgeService.retrieve("七天无理由")).thenReturn(new KnowledgeRetrievalResult(
                "七天无理由", List.of(hit), "context", List.of(1L), false));
        SearchKnowledgeTool tool = new SearchKnowledgeTool(knowledgeService);

        PreparedToolCall call = tool.prepare(ToolTestFixtures.context(), Map.of("query", "七天无理由"));
        ToolExecutionOutcome outcome = tool.execute(ToolTestFixtures.context(), call);

        assertThat(outcome.data()).containsEntry("count", 1);
        assertThat(outcome.data()).containsEntry("contextChunkIds", List.of(1L));
        assertThat(outcome.data().get("retrieval")).isInstanceOf(KnowledgeRetrievalResult.class);
        assertThat((List<?>) outcome.data().get("sources")).hasSize(1);
    }
}

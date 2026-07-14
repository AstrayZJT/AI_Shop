package com.aishop.assistant.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aishop.service.KnowledgeService;

class RagEvaluationServiceTest {

    @Test
    void calculatesHitAtKAndMeanReciprocalRankWithoutCallingChatModel() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        when(knowledgeService.retrieve(anyString())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0);
            String text;
            if (query.contains("七天")) {
                text = "商品支持七天无理由退货。";
            } else if (query.contains("退款")) {
                text = "退款申请会进入审核。";
            } else if (query.contains("物流")) {
                text = "物流信息在发货后更新。";
            } else if (query.contains("发票")) {
                text = "用户可以申请电子发票。";
            } else {
                return new KnowledgeRetrievalResult(query, List.of(), "", List.of(), false);
            }
            return RagTestFixtures.retrieval(RagTestFixtures.hit(1L, text));
        });

        RagEvaluationResult result = new RagEvaluationService(knowledgeService).evaluate();

        assertThat(result.totalCases()).isEqualTo(5);
        assertThat(result.hitCases()).isEqualTo(4);
        assertThat(result.hitAtK()).isEqualTo(0.8);
        assertThat(result.meanReciprocalRank()).isEqualTo(0.8);
    }
}

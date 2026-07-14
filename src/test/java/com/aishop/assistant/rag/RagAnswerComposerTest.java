package com.aishop.assistant.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aishop.service.KnowledgeService;
import com.fasterxml.jackson.databind.ObjectMapper;

class RagAnswerComposerTest {

    @Test
    void doesNotCallModelWithoutReliableEvidence() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        RagAnswerModelGateway gateway = mock(RagAnswerModelGateway.class);
        when(knowledgeService.retrieve("未知政策")).thenReturn(
                new KnowledgeRetrievalResult("未知政策", List.of(), "", List.of(), false));

        RagAnswerResult result = composer(knowledgeService, gateway, true).compose("未知政策");

        assertThat(result.mode()).isEqualTo(RagAnswerMode.NO_EVIDENCE);
        assertThat(result.grounded()).isFalse();
        assertThat(result.citations()).isEmpty();
        verifyNoInteractions(gateway);
    }

    @Test
    void returnsExtractiveEvidenceWhenRemoteModelIsDisabled() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        RagAnswerModelGateway gateway = mock(RagAnswerModelGateway.class);
        when(knowledgeService.retrieve("退货")).thenReturn(
                RagTestFixtures.retrieval(RagTestFixtures.hit(1L, "签收后七天内可以申请无理由退货。")));

        RagAnswerResult result = composer(knowledgeService, gateway, false).compose("退货");

        assertThat(result.mode()).isEqualTo(RagAnswerMode.RETRIEVAL_ONLY);
        assertThat(result.grounded()).isTrue();
        assertThat(result.answer()).contains("七天内");
        assertThat(result.citations()).extracting(RagCitation::chunkId).containsExactly(1L);
        verifyNoInteractions(gateway);
    }

    @Test
    void acceptsOnlyCitationsSelectedFromCurrentContext() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        RagAnswerModelGateway gateway = mock(RagAnswerModelGateway.class);
        when(knowledgeService.retrieve("退货")).thenReturn(RagTestFixtures.retrieval(
                RagTestFixtures.hit(1L, "七天无理由退货规则。"),
                RagTestFixtures.hit(2L, "退款将在审核后处理。")));
        when(gateway.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new RagAnswerModelReply(
                        "{\"answer\":\"签收后七天内可申请。\",\"usedChunkIds\":[1],\"sufficient\":true}",
                        "qwen-plus", 100, 20));

        RagAnswerResult result = composer(knowledgeService, gateway, true).compose("退货");

        assertThat(result.mode()).isEqualTo(RagAnswerMode.MODEL_GROUNDED);
        assertThat(result.grounded()).isTrue();
        assertThat(result.citations()).extracting(RagCitation::chunkId).containsExactly(1L);
        assertThat(result.modelName()).isEqualTo("qwen-plus");
    }

    @Test
    void rejectsCitationOutsideCurrentContextAndFallsBackToQuote() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        RagAnswerModelGateway gateway = mock(RagAnswerModelGateway.class);
        when(knowledgeService.retrieve("退货")).thenReturn(
                RagTestFixtures.retrieval(RagTestFixtures.hit(1L, "七天无理由退货规则。")));
        when(gateway.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new RagAnswerModelReply(
                        "{\"answer\":\"可以退款\",\"usedChunkIds\":[999],\"sufficient\":true}",
                        "qwen-plus", 100, 20));

        RagAnswerResult result = composer(knowledgeService, gateway, true).compose("退货");

        assertThat(result.mode()).isEqualTo(RagAnswerMode.MODEL_FALLBACK);
        assertThat(result.answer()).contains("原文依据", "七天无理由");
        assertThat(result.citations()).extracting(RagCitation::chunkId).containsExactly(1L);
    }

    @Test
    void respectsModelInsufficientDecisionWithoutCitations() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        RagAnswerModelGateway gateway = mock(RagAnswerModelGateway.class);
        when(knowledgeService.retrieve("特殊赔偿")).thenReturn(
                RagTestFixtures.retrieval(RagTestFixtures.hit(1L, "这里只介绍普通退货。")));
        when(gateway.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new RagAnswerModelReply(
                        "{\"answer\":\"无法确认\",\"usedChunkIds\":[],\"sufficient\":false}",
                        "qwen-plus", 80, 10));

        RagAnswerResult result = composer(knowledgeService, gateway, true).compose("特殊赔偿");

        assertThat(result.mode()).isEqualTo(RagAnswerMode.MODEL_UNCERTAIN);
        assertThat(result.grounded()).isFalse();
        assertThat(result.citations()).isEmpty();
    }

    @Test
    void rejectsMarkdownWrappedJsonAndUsesSafeFallback() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        RagAnswerModelGateway gateway = mock(RagAnswerModelGateway.class);
        when(knowledgeService.retrieve("退货")).thenReturn(
                RagTestFixtures.retrieval(RagTestFixtures.hit(1L, "七天无理由退货规则。")));
        when(gateway.answer(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new RagAnswerModelReply(
                        "```json\n{\"answer\":\"可以\",\"usedChunkIds\":[1],\"sufficient\":true}\n```",
                        "qwen-plus", 100, 20));

        RagAnswerResult result = composer(knowledgeService, gateway, true).compose("退货");

        assertThat(result.mode()).isEqualTo(RagAnswerMode.MODEL_FALLBACK);
        assertThat(result.answer()).doesNotContain("```json");
    }

    private RagAnswerComposer composer(KnowledgeService knowledgeService,
                                       RagAnswerModelGateway gateway,
                                       boolean aiEnabled) {
        ObjectMapper objectMapper = new ObjectMapper();
        return new RagAnswerComposer(
                knowledgeService,
                new RagPromptFactory(objectMapper),
                gateway,
                RagTestFixtures.properties(aiEnabled),
                objectMapper);
    }
}

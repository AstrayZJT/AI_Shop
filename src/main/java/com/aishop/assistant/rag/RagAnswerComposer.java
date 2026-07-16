package com.aishop.assistant.rag;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.aishop.config.ShopProperties;
import com.aishop.dto.KnowledgeDtos.SearchResponse;
import com.aishop.service.KnowledgeService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

@Service
public class RagAnswerComposer {

    private static final String NO_EVIDENCE_ANSWER = "知识库中没有找到足够可靠的依据，我暂时无法确认该规则。";
    private static final int MAX_ANSWER_LENGTH = 3000;

    private final KnowledgeService knowledgeService;
    private final RagPromptFactory promptFactory;
    private final RagAnswerModelGateway modelGateway;
    private final ShopProperties properties;
    private final ObjectReader answerReader;

    public RagAnswerComposer(KnowledgeService knowledgeService,
                             RagPromptFactory promptFactory,
                             RagAnswerModelGateway modelGateway,
                             ShopProperties properties,
                             ObjectMapper objectMapper) {
        this.knowledgeService = knowledgeService;
        this.promptFactory = promptFactory;
        this.modelGateway = modelGateway;
        this.properties = properties;
        this.answerReader = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readerFor(ModelAnswer.class);
    }

    public RagAnswerResult compose(String question) {
        KnowledgeRetrievalResult retrieval = knowledgeService.retrieve(question);
        return compose(question, retrieval);
    }

    public RagAnswerResult compose(String question, KnowledgeRetrievalResult retrieval) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question 不能为空");
        }
        if (retrieval == null) {
            throw new IllegalArgumentException("retrieval 不能为空");
        }
        if (!retrieval.hasReliableEvidence()) {
            return result(NO_EVIDENCE_ANSWER, RagAnswerMode.NO_EVIDENCE, false,
                    List.of(), retrieval, null);
        }
        if (!remoteModelAvailable()) {
            return retrievalOnly(retrieval, RagAnswerMode.RETRIEVAL_ONLY, null);
        }

        RagAnswerModelReply reply = null;
        try {
            var prompt = promptFactory.create(question, retrieval);
            reply = modelGateway.answer(prompt.systemPrompt(), prompt.userPrompt());
            ModelAnswer modelAnswer = parse(reply.content());
            if (!modelAnswer.sufficient()) {
                return result(NO_EVIDENCE_ANSWER, RagAnswerMode.MODEL_UNCERTAIN, false,
                        List.of(), retrieval, reply);
            }
            List<Long> usedIds = validateUsedChunkIds(modelAnswer, retrieval.contextChunkIds());
            List<RagCitation> citations = citations(retrieval.hits(), usedIds);
            return result(modelAnswer.answer().strip(), RagAnswerMode.MODEL_GROUNDED, true,
                    citations, retrieval, reply);
        } catch (RuntimeException ex) {
            return retrievalOnly(retrieval, RagAnswerMode.MODEL_FALLBACK, reply);
        }
    }

    private ModelAnswer parse(String content) {
        if (content == null || content.isBlank() || content.length() > 16_000) {
            throw new IllegalArgumentException("模型没有返回合法的 RAG JSON");
        }
        try {
            ModelAnswer answer = answerReader.readValue(content);
            if (answer.answer() == null || answer.answer().isBlank()
                    || answer.answer().length() > MAX_ANSWER_LENGTH
                    || answer.usedChunkIds() == null) {
                throw new IllegalArgumentException("模型 RAG 回答字段不完整");
            }
            return answer;
        } catch (Exception ex) {
            throw new IllegalArgumentException("模型 RAG 回答解析失败", ex);
        }
    }

    private List<Long> validateUsedChunkIds(ModelAnswer answer, List<Long> allowedIds) {
        LinkedHashSet<Long> usedIds = answer.usedChunkIds().stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (usedIds.isEmpty()) {
            throw new IllegalArgumentException("模型声称证据充分但没有引用 chunk");
        }
        Set<Long> allowed = Set.copyOf(allowedIds);
        if (!allowed.containsAll(usedIds)) {
            throw new IllegalArgumentException("模型引用了本次上下文之外的 chunk");
        }
        return List.copyOf(usedIds);
    }

    private RagAnswerResult retrievalOnly(KnowledgeRetrievalResult retrieval,
                                          RagAnswerMode mode,
                                          RagAnswerModelReply reply) {
        List<Long> usedIds = retrieval.contextChunkIds().stream().limit(2).toList();
        List<RagCitation> citations = citations(retrieval.hits(), usedIds);
        String quote = citations.isEmpty() ? NO_EVIDENCE_ANSWER : citations.get(0).quote();
        String answer = mode == RagAnswerMode.MODEL_FALLBACK
                ? "模型回答暂时不可用。知识库检索到的原文依据是：" + quote
                : "根据知识库原文：" + quote;
        return result(answer, mode, !citations.isEmpty(), citations, retrieval, reply);
    }

    private List<RagCitation> citations(List<SearchResponse> hits, List<Long> usedIds) {
        Map<Long, SearchResponse> byId = hits.stream()
                .collect(Collectors.toMap(SearchResponse::id, Function.identity(), (left, right) -> left));
        return usedIds.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(this::toCitation)
                .toList();
    }

    private RagCitation toCitation(SearchResponse hit) {
        return new RagCitation(
                hit.id(), hit.documentId(), hit.title(), hit.docType(), hit.chunkIndex(),
                hit.startOffset(), hit.endOffset(), hit.chunkText(), hit.score());
    }

    private RagAnswerResult result(String answer,
                                   RagAnswerMode mode,
                                   boolean grounded,
                                   List<RagCitation> citations,
                                   KnowledgeRetrievalResult retrieval,
                                   RagAnswerModelReply reply) {
        return new RagAnswerResult(
                answer,
                mode,
                grounded,
                citations,
                retrieval.hits(),
                retrieval.hits().size(),
                retrieval.contextTruncated(),
                RagPromptFactory.VERSION,
                fallbackReason(mode),
                reply == null ? null : reply.modelName(),
                reply == null ? null : reply.inputTokens(),
                reply == null ? null : reply.outputTokens());
    }

    private String fallbackReason(RagAnswerMode mode) {
        return switch (mode) {
            case MODEL_GROUNDED -> null;
            case RETRIEVAL_ONLY -> "REMOTE_MODEL_DISABLED";
            case NO_EVIDENCE -> "NO_RELIABLE_EVIDENCE";
            case MODEL_UNCERTAIN -> "MODEL_REPORTED_INSUFFICIENT";
            case MODEL_FALLBACK -> "MODEL_OUTPUT_INVALID_OR_FAILED";
        };
    }

    private boolean remoteModelAvailable() {
        return properties.ai().enabled()
                && properties.ai().apiKey() != null
                && !properties.ai().apiKey().isBlank();
    }

    private record ModelAnswer(String answer, List<Long> usedChunkIds, boolean sufficient) {
    }
}

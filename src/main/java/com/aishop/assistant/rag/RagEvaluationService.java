package com.aishop.assistant.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.aishop.assistant.rag.RagEvaluationResult.RagEvaluationCaseResult;
import com.aishop.dto.KnowledgeDtos.SearchResponse;
import com.aishop.service.KnowledgeService;

@Service
public class RagEvaluationService {

    private static final List<EvaluationCase> CASES = List.of(
            new EvaluationCase("return-policy", "七天无理由退货需要满足什么条件", List.of("七天", "无理由")),
            new EvaluationCase("refund", "申请退款后怎么处理", List.of("退款")),
            new EvaluationCase("logistics", "订单发货和物流规则是什么", List.of("物流")),
            new EvaluationCase("invoice", "购买商品后如何开发票", List.of("发票")),
            new EvaluationCase("warranty", "商品保修政策是什么", List.of("保修")));

    private final KnowledgeService knowledgeService;

    public RagEvaluationService(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    public RagEvaluationResult evaluate() {
        List<RagEvaluationCaseResult> results = new ArrayList<>();
        double reciprocalRankSum = 0;
        int hitCount = 0;
        for (EvaluationCase evaluationCase : CASES) {
            var retrieval = knowledgeService.retrieve(evaluationCase.query());
            Integer rank = firstRelevantRank(retrieval.hits(), evaluationCase.expectedTerms());
            boolean hit = rank != null;
            if (hit) {
                hitCount++;
                reciprocalRankSum += 1.0 / rank;
            }
            results.add(new RagEvaluationCaseResult(
                    evaluationCase.id(),
                    evaluationCase.query(),
                    evaluationCase.expectedTerms(),
                    hit,
                    rank,
                    retrieval.hits().stream().map(SearchResponse::id).toList()));
        }
        int total = results.size();
        return new RagEvaluationResult(
                total,
                hitCount,
                round(total == 0 ? 0 : hitCount / (double) total),
                round(total == 0 ? 0 : reciprocalRankSum / total),
                results);
    }

    private Integer firstRelevantRank(List<SearchResponse> hits, List<String> expectedTerms) {
        for (int i = 0; i < hits.size(); i++) {
            String text = hits.get(i).chunkText().toLowerCase(Locale.ROOT);
            if (expectedTerms.stream().allMatch(text::contains)) {
                return i + 1;
            }
        }
        return null;
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private record EvaluationCase(String id, String query, List<String> expectedTerms) {
    }
}

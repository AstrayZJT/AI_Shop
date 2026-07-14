package com.aishop.assistant.rag;

import java.util.List;

public record RagEvaluationResult(
        int totalCases,
        int hitCases,
        double hitAtK,
        double meanReciprocalRank,
        List<RagEvaluationCaseResult> cases
) {
    public RagEvaluationResult {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }

    public record RagEvaluationCaseResult(
            String id,
            String query,
            List<String> expectedTerms,
            boolean hit,
            Integer firstRelevantRank,
            List<Long> returnedChunkIds
    ) {
        public RagEvaluationCaseResult {
            expectedTerms = expectedTerms == null ? List.of() : List.copyOf(expectedTerms);
            returnedChunkIds = returnedChunkIds == null ? List.of() : List.copyOf(returnedChunkIds);
        }
    }
}

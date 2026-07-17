package com.aishop.assistant.evaluation;

import java.util.List;

public record PlannerEvaluationResult(
        String mode,
        String dataset,
        int totalCases,
        int passedCases,
        int failedCases,
        double intentAccuracy,
        double actionExactMatch,
        double slotExactMatch,
        double taskCountAccuracy,
        double multiTaskAccuracy,
        double dependencyExactMatch,
        double ruleFallbackRate,
        List<PlannerCaseResult> cases) {

    public PlannerEvaluationResult {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }

    public record PlannerCaseResult(
            String id,
            boolean passed,
            boolean intentMatched,
            boolean actionMatched,
            boolean slotsMatched,
            boolean taskCountMatched,
            boolean multiTaskMatched,
            boolean dependenciesMatched,
            String expectedPlanType,
            String actualPlanType,
            List<String> expectedActions,
            List<String> actualActions,
            String plannerSource,
            String fallbackReason,
            String failureReason) {
    }
}

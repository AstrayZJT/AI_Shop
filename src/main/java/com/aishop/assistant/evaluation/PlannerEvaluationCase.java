package com.aishop.assistant.evaluation;

import java.util.List;
import java.util.Map;

public record PlannerEvaluationCase(
        String id,
        String message,
        String conversationSummary,
        List<String> recentMessages,
        String expectedPlanType,
        List<String> expectedIntents,
        List<String> expectedActions,
        int expectedTaskCount,
        Map<String, Map<String, Object>> expectedSlotsByAction,
        Map<String, List<String>> expectedDependsOnByAction) {

    public PlannerEvaluationCase {
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
        expectedIntents = expectedIntents == null ? List.of() : List.copyOf(expectedIntents);
        expectedActions = expectedActions == null ? List.of() : List.copyOf(expectedActions);
        expectedSlotsByAction = expectedSlotsByAction == null ? Map.of() : Map.copyOf(expectedSlotsByAction);
        expectedDependsOnByAction = expectedDependsOnByAction == null
                ? Map.of()
                : Map.copyOf(expectedDependsOnByAction);
    }
}

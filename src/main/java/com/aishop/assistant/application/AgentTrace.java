package com.aishop.assistant.application;

import java.util.List;

public record AgentTrace(
        String traceId,
        Long sessionId,
        Long planRunId,
        boolean resumed,
        String plannerSource,
        String fallbackReason,
        String modelName,
        Integer inputTokens,
        Integer outputTokens,
        long totalLatencyMs,
        long planningLatencyMs,
        long executionLatencyMs,
        long answerLatencyMs,
        int taskCount,
        List<AgentTaskTrace> tasks,
        String ragMode,
        int sourceCount,
        String answerMode,
        String finalStatus) {

    public AgentTrace {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }

    public static AgentTrace empty() {
        return new AgentTrace(
                "unknown", null, null, false, null, null, null, null, null,
                0, 0, 0, 0, 0, List.of(), null, 0, null, null);
    }
}

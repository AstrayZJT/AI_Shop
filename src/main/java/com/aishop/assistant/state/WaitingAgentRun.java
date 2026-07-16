package com.aishop.assistant.state;

public record WaitingAgentRun(
        Long planRunId,
        AgentRunStatus status,
        String planSummary,
        String currentTaskId) {
}

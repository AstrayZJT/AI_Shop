package com.aishop.assistant.state;

import com.aishop.assistant.orchestration.ToolPlanExecutionResult;

public record StateMachineExecution(
        Long planRunId,
        AgentRunStatus status,
        Long pendingActionId,
        boolean resumed,
        ToolPlanExecutionResult execution) {
}

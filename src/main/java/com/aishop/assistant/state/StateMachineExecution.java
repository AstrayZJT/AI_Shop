package com.aishop.assistant.state;

import com.aishop.assistant.orchestration.ToolPlanExecutionResult;

public record StateMachineExecution(
        Long planRunId,
        AgentRunStatus status,
        Long pendingActionId,
        boolean resumed,
        ToolPlanExecutionResult execution,
        boolean idempotentReplay) {

    public StateMachineExecution(Long planRunId,
                                 AgentRunStatus status,
                                 Long pendingActionId,
                                 boolean resumed,
                                 ToolPlanExecutionResult execution) {
        this(planRunId, status, pendingActionId, resumed, execution, false);
    }
}

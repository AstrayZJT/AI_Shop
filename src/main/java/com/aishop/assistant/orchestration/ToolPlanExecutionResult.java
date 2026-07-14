package com.aishop.assistant.orchestration;

import java.util.List;

import com.aishop.assistant.model.PlannerResult;
import com.aishop.assistant.tool.TaskToolResult;

public record ToolPlanExecutionResult(
        PlannerResult planner,
        List<TaskToolResult> taskResults
) {
    public ToolPlanExecutionResult {
        taskResults = taskResults == null ? List.of() : List.copyOf(taskResults);
    }
}

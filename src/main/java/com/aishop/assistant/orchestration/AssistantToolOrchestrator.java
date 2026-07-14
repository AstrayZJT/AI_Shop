package com.aishop.assistant.orchestration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.AssistantTask;
import com.aishop.assistant.model.PlannerResult;
import com.aishop.assistant.tool.AssistantTool;
import com.aishop.assistant.tool.AssistantToolRegistry;
import com.aishop.assistant.tool.PreparedToolCall;
import com.aishop.assistant.tool.TaskToolResult;
import com.aishop.assistant.tool.ToolContext;
import com.aishop.assistant.tool.ToolExecutionOutcome;
import com.aishop.assistant.tool.ToolExecutionStatus;
import com.aishop.assistant.tool.ToolRiskLevel;
import com.aishop.domain.AppUser;

@Service
public class AssistantToolOrchestrator {

    private final AssistantToolRegistry toolRegistry;

    public AssistantToolOrchestrator(AssistantToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public ToolPlanExecutionResult executePlan(AppUser user, PlannerResult plannerResult) {
        ToolContext context = new ToolContext(user, UUID.randomUUID().toString());
        List<AssistantTask> sortedTasks = sort(plannerResult.plan());
        Map<String, TaskToolResult> completed = new LinkedHashMap<>();

        for (AssistantTask task : sortedTasks) {
            TaskToolResult result;
            if (!dependenciesSucceeded(task, completed)) {
                result = result(
                        task.taskId(), task.action(), null, ToolExecutionStatus.SKIPPED_DEPENDENCY,
                        task.slots(), null, Map.of(), "依赖任务未成功，当前任务未执行");
            } else if (task.missingSlots() != null && !task.missingSlots().isEmpty()) {
                result = result(
                        task.taskId(), task.action(), null, ToolExecutionStatus.NEEDS_INPUT,
                        task.slots(), null, Map.of("missingSlots", task.missingSlots()), "任务缺少必要参数");
            } else {
                result = toolRegistry.find(task.action())
                        .map(tool -> executeTool(context, task.taskId(), tool, task.slots()))
                        .orElseGet(() -> result(
                                task.taskId(), task.action(), null, ToolExecutionStatus.NOT_SUPPORTED,
                                task.slots(), null, Map.of(), "当前阶段没有为该 action 注册工具"));
            }
            completed.put(task.taskId(), result);
        }
        return new ToolPlanExecutionResult(plannerResult, List.copyOf(completed.values()));
    }

    public TaskToolResult executeNativeCall(ToolContext context,
                                            String callId,
                                            String toolName,
                                            Map<String, Object> arguments) {
        return toolRegistry.find(toolName)
                .map(tool -> executeTool(context, callId, tool, arguments))
                .orElseGet(() -> result(
                        callId, null, toolName, ToolExecutionStatus.NOT_SUPPORTED,
                        arguments, null, Map.of(), "模型请求了未注册工具"));
    }

    private TaskToolResult executeTool(ToolContext context,
                                       String taskId,
                                       AssistantTool tool,
                                       Map<String, Object> arguments) {
        try {
            PreparedToolCall call = tool.prepare(context, arguments == null ? Map.of() : arguments);
            if (!tool.policy().autoExecutable()) {
                return result(
                        taskId,
                        tool.policy().action(),
                        tool.policy().name(),
                        ToolExecutionStatus.PREPARED,
                        call.arguments(),
                        call.targetRef(),
                        call.preview(),
                        "高风险动作仅完成准备，尚未执行业务变更");
            }
            if (tool.policy().riskLevel() != ToolRiskLevel.READ_ONLY) {
                throw new SecurityException("非只读工具不能自动执行");
            }
            ToolExecutionOutcome outcome = tool.execute(context, call);
            return result(
                    taskId,
                    tool.policy().action(),
                    tool.policy().name(),
                    ToolExecutionStatus.SUCCEEDED,
                    call.arguments(),
                    call.targetRef(),
                    outcome.data(),
                    outcome.message());
        } catch (RuntimeException ex) {
            return result(
                    taskId,
                    tool.policy().action(),
                    tool.policy().name(),
                    ToolExecutionStatus.FAILED,
                    arguments,
                    null,
                    Map.of("errorType", ex.getClass().getSimpleName()),
                    safeMessage(ex));
        }
    }

    private boolean dependenciesSucceeded(AssistantTask task, Map<String, TaskToolResult> completed) {
        if (task.dependsOn() == null || task.dependsOn().isEmpty()) {
            return true;
        }
        for (String dependency : task.dependsOn()) {
            TaskToolResult result = completed.get(dependency);
            if (result == null
                    || !(result.status() == ToolExecutionStatus.SUCCEEDED
                    || result.status() == ToolExecutionStatus.PREPARED)) {
                return false;
            }
        }
        return true;
    }

    private List<AssistantTask> sort(AssistantPlan plan) {
        Map<String, AssistantTask> tasks = new LinkedHashMap<>();
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> graph = new HashMap<>();
        for (AssistantTask task : plan.tasks()) {
            tasks.put(task.taskId(), task);
            indegree.put(task.taskId(), task.dependsOn() == null ? 0 : task.dependsOn().size());
            graph.put(task.taskId(), new ArrayList<>());
        }
        for (AssistantTask task : plan.tasks()) {
            if (task.dependsOn() == null) {
                continue;
            }
            for (String dependency : task.dependsOn()) {
                graph.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(task.taskId());
            }
        }
        Queue<String> queue = new ArrayDeque<>();
        for (String taskId : tasks.keySet()) {
            if (indegree.get(taskId) == 0) {
                queue.add(taskId);
            }
        }
        List<AssistantTask> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.remove();
            sorted.add(tasks.get(current));
            for (String next : graph.getOrDefault(current, List.of())) {
                int nextDegree = indegree.computeIfPresent(next, (ignored, degree) -> degree - 1);
                if (nextDegree == 0) {
                    queue.add(next);
                }
            }
        }
        if (sorted.size() != tasks.size()) {
            throw new IllegalArgumentException("任务依赖存在循环，无法执行工具");
        }
        return sorted;
    }

    private TaskToolResult result(String taskId,
                                  com.aishop.assistant.model.AssistantAction action,
                                  String toolName,
                                  ToolExecutionStatus status,
                                  Map<String, Object> arguments,
                                  String targetRef,
                                  Map<String, Object> data,
                                  String message) {
        return new TaskToolResult(taskId, action, toolName, status, arguments, targetRef, data, message);
    }

    private String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "工具调用失败" : message;
    }
}

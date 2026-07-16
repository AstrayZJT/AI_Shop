package com.aishop.assistant.state;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.AssistantTask;
import com.aishop.assistant.model.ExecutionMode;
import com.aishop.assistant.model.PlannerResult;
import com.aishop.assistant.model.PlannerSource;
import com.aishop.assistant.orchestration.AssistantToolOrchestrator;
import com.aishop.assistant.orchestration.ToolPlanExecutionResult;
import com.aishop.assistant.tool.TaskToolResult;
import com.aishop.assistant.tool.ToolContext;
import com.aishop.assistant.tool.ToolExecutionOutcome;
import com.aishop.assistant.tool.ToolExecutionStatus;
import com.aishop.config.AssistantStateProperties;
import com.aishop.domain.AppUser;
import com.aishop.domain.AssistantPlanRun;
import com.aishop.domain.AssistantSession;
import com.aishop.domain.AssistantTaskRun;
import com.aishop.domain.PendingAssistantAction;
import com.aishop.repository.AssistantPlanRunRepository;
import com.aishop.repository.AssistantTaskRunRepository;
import com.aishop.repository.PendingAssistantActionRepository;
import com.aishop.assistant.validation.PlanValidator;

@Service
public class AssistantStateMachine {

    private static final EnumSet<AgentRunStatus> WAITING_STATUSES = EnumSet.of(
            AgentRunStatus.WAITING_INPUT,
            AgentRunStatus.WAITING_CONFIRMATION);
    private static final EnumSet<AgentRunStatus> SUCCESSFUL_TASK_STATUSES = EnumSet.of(
            AgentRunStatus.SUCCEEDED,
            AgentRunStatus.SKIPPED);
    private static final EnumSet<AgentRunStatus> TERMINAL_TASK_STATUSES = EnumSet.of(
            AgentRunStatus.SUCCEEDED,
            AgentRunStatus.SKIPPED,
            AgentRunStatus.FAILED,
            AgentRunStatus.EXPIRED);

    private final AssistantPlanRunRepository planRunRepository;
    private final AssistantTaskRunRepository taskRunRepository;
    private final PendingAssistantActionRepository pendingActionRepository;
    private final AssistantToolOrchestrator toolOrchestrator;
    private final TaskSorter taskSorter;
    private final TaskConflictAnalyzer conflictAnalyzer;
    private final ConditionEvaluator conditionEvaluator;
    private final PendingInputResolver inputResolver;
    private final ConfirmationDecisionResolver confirmationResolver;
    private final ConfirmedActionExecutor confirmedActionExecutor;
    private final AgentStateCodec codec;
    private final PlanValidator planValidator;
    private final AssistantStateProperties properties;

    public AssistantStateMachine(AssistantPlanRunRepository planRunRepository,
                                 AssistantTaskRunRepository taskRunRepository,
                                 PendingAssistantActionRepository pendingActionRepository,
                                 AssistantToolOrchestrator toolOrchestrator,
                                 TaskSorter taskSorter,
                                 TaskConflictAnalyzer conflictAnalyzer,
                                 ConditionEvaluator conditionEvaluator,
                                 PendingInputResolver inputResolver,
                                 ConfirmationDecisionResolver confirmationResolver,
                                 ConfirmedActionExecutor confirmedActionExecutor,
                                 AgentStateCodec codec,
                                 PlanValidator planValidator,
                                 AssistantStateProperties properties) {
        this.planRunRepository = planRunRepository;
        this.taskRunRepository = taskRunRepository;
        this.pendingActionRepository = pendingActionRepository;
        this.toolOrchestrator = toolOrchestrator;
        this.taskSorter = taskSorter;
        this.conflictAnalyzer = conflictAnalyzer;
        this.conditionEvaluator = conditionEvaluator;
        this.inputResolver = inputResolver;
        this.confirmationResolver = confirmationResolver;
        this.confirmedActionExecutor = confirmedActionExecutor;
        this.codec = codec;
        this.planValidator = planValidator;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Optional<WaitingAgentRun> findWaiting(AppUser user, AssistantSession session) {
        return findWaitingRun(user, session)
                .map(run -> new WaitingAgentRun(
                        run.getId(), run.getStatus(), codec.readPlan(run.getPlanJson()).summary(),
                        run.getCurrentTaskId()));
    }

    @Transactional
    public StateMachineExecution start(AppUser user,
                                       AssistantSession session,
                                       PlannerResult plannerResult) {
        if (findWaitingRun(user, session).isPresent()) {
            throw new IllegalStateException("当前会话仍有等待中的 Agent 计划");
        }
        AssistantPlan plan = planValidator.validate(plannerResult.plan());
        AssistantPlanRun planRun = createPlanRun(user, session, plannerResult);
        List<AssistantTask> sorted = taskSorter.sort(plan);
        createTaskRuns(planRun, sorted);

        List<TaskConflict> conflicts = conflictAnalyzer.analyze(plan);
        if (!conflicts.isEmpty()) {
            return failConflictingPlan(planRun, plannerResult, conflicts);
        }
        planRun.setStatus(AgentRunStatus.RUNNING);
        planRunRepository.save(planRun);
        return drive(planRun, plannerResult, false);
    }

    @Transactional
    public StateMachineExecution resume(AppUser user,
                                        AssistantSession session,
                                        String userMessage) {
        AssistantPlanRun planRun = findWaitingRun(user, session)
                .orElseThrow(() -> new IllegalStateException("当前会话没有可恢复的 Agent 计划"));
        PlannerResult plannerResult = plannerResult(planRun);
        AssistantTaskRun taskRun = currentTaskRun(planRun);
        if (isExpired(planRun)) {
            return expire(planRun, taskRun, plannerResult);
        }
        return switch (planRun.getStatus()) {
            case WAITING_INPUT -> resumeInput(planRun, taskRun, plannerResult, userMessage);
            case WAITING_CONFIRMATION -> resumeConfirmation(
                    user, planRun, taskRun, plannerResult, userMessage);
            default -> throw new IllegalStateException("计划状态不可恢复: " + planRun.getStatus());
        };
    }

    private StateMachineExecution resumeInput(AssistantPlanRun planRun,
                                              AssistantTaskRun taskRun,
                                              PlannerResult plannerResult,
                                              String userMessage) {
        AssistantTask task = codec.readTask(taskRun.getTaskJson());
        AssistantTask resolved = inputResolver.resolve(task, userMessage);
        AssistantPlan updatedPlan = propagateResolvedOrderNo(
                replaceTask(plannerResult.plan(), resolved), resolved);
        planValidator.validate(updatedPlan);
        planRun.setPlanJson(codec.write(updatedPlan));
        synchronizeTaskDefinitions(planRun, updatedPlan);
        if (!resolved.missingSlots().isEmpty()) {
            TaskToolResult result = result(
                    resolved, ToolExecutionStatus.NEEDS_INPUT, null, null,
                    Map.of("missingSlots", resolved.missingSlots()), "仍缺少必要参数");
            saveTaskResult(taskRun, AgentRunStatus.WAITING_INPUT, result, false);
            taskRunRepository.save(taskRun);
            planRunRepository.save(planRun);
            return execution(planRun, plannerResult(planRun), null, true);
        }
        taskRun.setStatus(AgentRunStatus.PENDING);
        taskRun.setResultJson(null);
        taskRun.setResultMessage(null);
        taskRunRepository.save(taskRun);
        clearWait(planRun);
        planRun.setStatus(AgentRunStatus.RUNNING);
        planRunRepository.save(planRun);
        return drive(planRun, plannerResult(planRun), true);
    }

    private StateMachineExecution resumeConfirmation(AppUser user,
                                                     AssistantPlanRun planRun,
                                                     AssistantTaskRun taskRun,
                                                     PlannerResult plannerResult,
                                                     String userMessage) {
        PendingAssistantAction pending = pendingActionRepository.findByTaskRun(taskRun)
                .orElseThrow(() -> new IllegalStateException("等待确认任务缺少 PendingAction"));
        ConfirmationDecision decision = confirmationResolver.resolve(userMessage);
        if (decision == ConfirmationDecision.UNKNOWN) {
            return execution(planRun, plannerResult, pending.getId(), true);
        }
        if (decision == ConfirmationDecision.REJECT) {
            Instant now = Instant.now();
            pending.setStatus(PendingActionStatus.REJECTED);
            pending.setRejectedAt(now);
            pending.setResultMessage("用户拒绝执行");
            pendingActionRepository.save(pending);
            AssistantTask task = codec.readTask(taskRun.getTaskJson());
            TaskToolResult rejected = result(
                    task, ToolExecutionStatus.REJECTED, pending.getTargetRef(), taskRun.getToolName(),
                    Map.of("pendingActionId", pending.getId()), "用户已取消本次操作");
            saveTaskResult(taskRun, AgentRunStatus.SKIPPED, rejected, true);
            taskRunRepository.save(taskRun);
            clearWait(planRun);
            planRun.setStatus(AgentRunStatus.RUNNING);
            planRunRepository.save(planRun);
            return drive(planRun, plannerResult, true);
        }
        return confirmAndContinue(user, planRun, taskRun, plannerResult, pending);
    }

    private StateMachineExecution confirmAndContinue(AppUser user,
                                                     AssistantPlanRun planRun,
                                                     AssistantTaskRun taskRun,
                                                     PlannerResult plannerResult,
                                                     PendingAssistantAction pendingCandidate) {
        PendingAssistantAction pending = pendingActionRepository
                .findOwnedByIdForUpdate(pendingCandidate.getId(), user)
                .orElseThrow(() -> new IllegalArgumentException("待确认动作不存在或不属于当前用户"));
        if (pending.getStatus() != PendingActionStatus.PENDING) {
            throw new IllegalStateException("待确认动作已经处理: " + pending.getStatus());
        }
        if (pending.getExpiresAt().isBefore(Instant.now())) {
            return expire(planRun, taskRun, plannerResult);
        }
        pending.setConfirmedAt(Instant.now());
        AssistantTask task = codec.readTask(taskRun.getTaskJson());
        try {
            ToolExecutionOutcome outcome = confirmedActionExecutor.execute(
                    user, pending.getAction(), codec.readMap(pending.getArgumentsJson()));
            pending.setStatus(PendingActionStatus.EXECUTED);
            pending.setExecutedAt(Instant.now());
            pending.setResultMessage(outcome.message());
            pendingActionRepository.save(pending);
            TaskToolResult succeeded = result(
                    task, ToolExecutionStatus.SUCCEEDED, pending.getTargetRef(), taskRun.getToolName(),
                    outcome.data(), outcome.message());
            saveTaskResult(taskRun, AgentRunStatus.SUCCEEDED, succeeded, true);
            taskRunRepository.save(taskRun);
            clearWait(planRun);
            planRun.setStatus(AgentRunStatus.RUNNING);
            planRunRepository.save(planRun);
            return drive(planRun, plannerResult, true);
        } catch (RuntimeException ex) {
            pending.setStatus(PendingActionStatus.FAILED);
            pending.setResultMessage(safeMessage(ex));
            pendingActionRepository.save(pending);
            TaskToolResult failed = result(
                    task, ToolExecutionStatus.FAILED, pending.getTargetRef(), taskRun.getToolName(),
                    Map.of("pendingActionId", pending.getId()), safeMessage(ex));
            saveTaskResult(taskRun, AgentRunStatus.FAILED, failed, true);
            taskRunRepository.save(taskRun);
            failPlan(planRun, safeMessage(ex));
            return execution(planRun, plannerResult, pending.getId(), true);
        }
    }

    private StateMachineExecution drive(AssistantPlanRun planRun,
                                        PlannerResult plannerResult,
                                        boolean resumed) {
        List<AssistantTask> sortedTasks = taskSorter.sort(plannerResult.plan());
        Map<String, AssistantTaskRun> runs = taskRunRepository.findByPlanRunOrderByTaskOrderAsc(planRun)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        AssistantTaskRun::getTaskId,
                        value -> value,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<String, TaskToolResult> completed = storedResults(runs);
        boolean failed = runs.values().stream().anyMatch(run -> run.getStatus() == AgentRunStatus.FAILED);

        for (AssistantTask task : sortedTasks) {
            AssistantTaskRun taskRun = runs.get(task.taskId());
            if (TERMINAL_TASK_STATUSES.contains(taskRun.getStatus())) {
                continue;
            }
            if (hasFailedDependency(task, runs)) {
                TaskToolResult skipped = result(
                        task, ToolExecutionStatus.SKIPPED_DEPENDENCY, null, null, Map.of(),
                        "依赖任务未成功，当前任务跳过");
                saveTaskResult(taskRun, AgentRunStatus.SKIPPED, skipped, true);
                taskRunRepository.save(taskRun);
                completed.put(task.taskId(), skipped);
                continue;
            }
            if (!dependenciesCompleted(task, runs)) {
                continue;
            }
            ConditionEvaluation condition = conditionEvaluator.evaluate(task.conditions(), completed);
            if (!condition.matched()) {
                TaskToolResult skipped = result(
                        task, ToolExecutionStatus.SKIPPED_DEPENDENCY, null, null,
                        Map.of("conditionReason", condition.reason()), "任务条件不满足，已跳过");
                saveTaskResult(taskRun, AgentRunStatus.SKIPPED, skipped, true);
                taskRunRepository.save(taskRun);
                completed.put(task.taskId(), skipped);
                continue;
            }
            if (!task.missingSlots().isEmpty()) {
                TaskToolResult needsInput = result(
                        task, ToolExecutionStatus.NEEDS_INPUT, null, null,
                        Map.of("missingSlots", task.missingSlots()), "任务缺少必要参数");
                saveTaskResult(taskRun, AgentRunStatus.WAITING_INPUT, needsInput, false);
                taskRunRepository.save(taskRun);
                waitFor(planRun, task.taskId(), AgentRunStatus.WAITING_INPUT,
                        Instant.now().plus(properties.inputTtl()));
                return execution(planRun, plannerResult, null, resumed);
            }

            taskRun.setStatus(AgentRunStatus.RUNNING);
            taskRun.setStartedAt(Instant.now());
            taskRunRepository.save(taskRun);
            TaskToolResult result = toolOrchestrator.executeTask(
                    new ToolContext(planRun.getUser(), "plan-run-" + planRun.getId()), task);
            if (result.status() == ToolExecutionStatus.PREPARED) {
                PendingAssistantAction pending = createPendingAction(planRun, taskRun, result);
                TaskToolResult waiting = withPendingMetadata(result, pending);
                saveTaskResult(taskRun, AgentRunStatus.WAITING_CONFIRMATION, waiting, false);
                taskRunRepository.save(taskRun);
                waitFor(planRun, task.taskId(), AgentRunStatus.WAITING_CONFIRMATION, pending.getExpiresAt());
                return execution(planRun, plannerResult, pending.getId(), resumed);
            }
            AgentRunStatus taskStatus = result.status() == ToolExecutionStatus.SUCCEEDED
                    || isSuccessfulAnswerOnly(task, result)
                    ? AgentRunStatus.SUCCEEDED
                    : AgentRunStatus.FAILED;
            saveTaskResult(taskRun, taskStatus, result, true);
            taskRunRepository.save(taskRun);
            completed.put(task.taskId(), result);
            failed = failed || taskStatus == AgentRunStatus.FAILED;
        }

        if (failed) {
            failPlan(planRun, "一个或多个任务执行失败");
        } else {
            completePlan(planRun);
        }
        return execution(planRun, plannerResult, null, resumed);
    }

    private PendingAssistantAction createPendingAction(AssistantPlanRun planRun,
                                                       AssistantTaskRun taskRun,
                                                       TaskToolResult result) {
        if (result.targetRef() == null || result.targetRef().isBlank()) {
            throw new IllegalArgumentException("高风险动作缺少可审计目标");
        }
        PendingAssistantAction pending = new PendingAssistantAction();
        pending.setPlanRun(planRun);
        pending.setTaskRun(taskRun);
        pending.setSession(planRun.getSession());
        pending.setUser(planRun.getUser());
        pending.setAction(result.action());
        pending.setStatus(PendingActionStatus.PENDING);
        pending.setTargetRef(result.targetRef());
        pending.setArgumentsJson(codec.write(result.arguments()));
        pending.setPreviewJson(codec.write(result.data()));
        pending.setExpiresAt(Instant.now().plus(properties.confirmationTtl()));
        return pendingActionRepository.save(pending);
    }

    private TaskToolResult withPendingMetadata(TaskToolResult result, PendingAssistantAction pending) {
        Map<String, Object> data = new LinkedHashMap<>(result.data());
        data.put("pendingActionId", pending.getId());
        data.put("expiresAt", pending.getExpiresAt().toString());
        return new TaskToolResult(
                result.taskId(), result.action(), result.toolName(), result.status(), result.arguments(),
                result.targetRef(), data, result.message());
    }

    private StateMachineExecution expire(AssistantPlanRun planRun,
                                         AssistantTaskRun taskRun,
                                         PlannerResult plannerResult) {
        Instant now = Instant.now();
        pendingActionRepository.findByTaskRun(taskRun).ifPresent(pending -> {
            if (pending.getStatus() == PendingActionStatus.PENDING) {
                pending.setStatus(PendingActionStatus.EXPIRED);
                pending.setResultMessage("待确认动作已过期");
                pendingActionRepository.save(pending);
            }
        });
        AssistantTask task = codec.readTask(taskRun.getTaskJson());
        TaskToolResult expired = result(
                task, ToolExecutionStatus.EXPIRED, taskRun.getTargetRef(), taskRun.getToolName(),
                Map.of(), "等待已超时，请重新发起请求");
        saveTaskResult(taskRun, AgentRunStatus.EXPIRED, expired, true);
        taskRun.setCompletedAt(now);
        taskRunRepository.save(taskRun);
        planRun.setStatus(AgentRunStatus.EXPIRED);
        planRun.setCompletedAt(now);
        clearWait(planRun);
        planRunRepository.save(planRun);
        return execution(planRun, plannerResult, null, true);
    }

    private StateMachineExecution failConflictingPlan(AssistantPlanRun planRun,
                                                      PlannerResult plannerResult,
                                                      List<TaskConflict> conflicts) {
        String reason = conflicts.stream().map(TaskConflict::reason).distinct()
                .reduce((left, right) -> left + "；" + right)
                .orElse("计划包含冲突任务");
        List<AssistantTaskRun> taskRuns = taskRunRepository.findByPlanRunOrderByTaskOrderAsc(planRun);
        for (AssistantTaskRun taskRun : taskRuns) {
            AssistantTask task = codec.readTask(taskRun.getTaskJson());
            TaskToolResult failed = result(
                    task, ToolExecutionStatus.FAILED, null, null,
                    Map.of("conflictCount", conflicts.size()), reason);
            saveTaskResult(taskRun, AgentRunStatus.FAILED, failed, true);
            taskRunRepository.save(taskRun);
        }
        failPlan(planRun, reason);
        return execution(planRun, plannerResult, null, false);
    }

    private AssistantPlanRun createPlanRun(AppUser user,
                                           AssistantSession session,
                                           PlannerResult plannerResult) {
        AssistantPlanRun run = new AssistantPlanRun();
        run.setUser(user);
        run.setSession(session);
        run.setStatus(AgentRunStatus.PENDING);
        run.setPlanJson(codec.write(plannerResult.plan()));
        run.setPlannerSource(plannerResult.source().name());
        run.setPromptVersion(plannerResult.promptVersion());
        run.setModelName(plannerResult.modelName());
        return planRunRepository.save(run);
    }

    private void createTaskRuns(AssistantPlanRun planRun, List<AssistantTask> tasks) {
        for (int index = 0; index < tasks.size(); index++) {
            AssistantTask task = tasks.get(index);
            AssistantTaskRun run = new AssistantTaskRun();
            run.setPlanRun(planRun);
            run.setTaskOrder(index);
            run.setTaskId(task.taskId());
            run.setIntent(task.intent());
            run.setAction(task.action());
            run.setExecutionMode(task.executionMode());
            run.setStatus(AgentRunStatus.PENDING);
            run.setTaskJson(codec.write(task));
            taskRunRepository.save(run);
        }
    }

    private StateMachineExecution execution(AssistantPlanRun planRun,
                                            PlannerResult plannerResult,
                                            Long pendingActionId,
                                            boolean resumed) {
        List<TaskToolResult> results = taskRunRepository.findByPlanRunOrderByTaskOrderAsc(planRun).stream()
                .map(AssistantTaskRun::getResultJson)
                .filter(java.util.Objects::nonNull)
                .map(codec::readResult)
                .toList();
        return new StateMachineExecution(
                planRun.getId(), planRun.getStatus(), pendingActionId, resumed,
                new ToolPlanExecutionResult(plannerResult, results));
    }

    private Map<String, TaskToolResult> storedResults(Map<String, AssistantTaskRun> runs) {
        Map<String, TaskToolResult> results = new LinkedHashMap<>();
        for (AssistantTaskRun run : runs.values()) {
            if (run.getResultJson() != null) {
                results.put(run.getTaskId(), codec.readResult(run.getResultJson()));
            }
        }
        return results;
    }

    private boolean dependenciesCompleted(AssistantTask task, Map<String, AssistantTaskRun> runs) {
        return task.dependsOn().stream()
                .map(runs::get)
                .allMatch(run -> run != null && SUCCESSFUL_TASK_STATUSES.contains(run.getStatus()));
    }

    private boolean hasFailedDependency(AssistantTask task, Map<String, AssistantTaskRun> runs) {
        return task.dependsOn().stream()
                .map(runs::get)
                .anyMatch(run -> run == null
                        || run.getStatus() == AgentRunStatus.FAILED
                        || run.getStatus() == AgentRunStatus.EXPIRED
                        || run.getStatus() == AgentRunStatus.SKIPPED);
    }

    private AssistantTaskRun currentTaskRun(AssistantPlanRun planRun) {
        return taskRunRepository.findByPlanRunAndTaskId(planRun, planRun.getCurrentTaskId())
                .orElseThrow(() -> new IllegalStateException("计划当前任务不存在"));
    }

    private Optional<AssistantPlanRun> findWaitingRun(AppUser user, AssistantSession session) {
        return planRunRepository.findTopBySessionAndUserAndStatusInOrderByCreatedAtDesc(
                session, user, WAITING_STATUSES);
    }

    private PlannerResult plannerResult(AssistantPlanRun run) {
        return new PlannerResult(
                codec.readPlan(run.getPlanJson()),
                PlannerSource.valueOf(run.getPlannerSource()),
                run.getPromptVersion(),
                null,
                null,
                run.getModelName(),
                null,
                null);
    }

    private AssistantPlan replaceTask(AssistantPlan plan, AssistantTask replacement) {
        List<AssistantTask> tasks = plan.tasks().stream()
                .map(task -> task.taskId().equals(replacement.taskId()) ? replacement : task)
                .toList();
        return new AssistantPlan(plan.planType(), tasks, plan.summary());
    }

    private AssistantPlan propagateResolvedOrderNo(AssistantPlan plan, AssistantTask source) {
        Object orderNo = source.slots().get("orderNo");
        if (!(orderNo instanceof String text) || text.isBlank()) {
            return plan;
        }
        List<AssistantTask> tasks = plan.tasks().stream()
                .map(task -> fillDependentOrderNo(task, source.taskId(), text))
                .toList();
        return new AssistantPlan(plan.planType(), tasks, plan.summary());
    }

    private AssistantTask fillDependentOrderNo(AssistantTask task, String sourceTaskId, String orderNo) {
        if (!task.dependsOn().contains(sourceTaskId) || !task.missingSlots().contains("orderNo")) {
            return task;
        }
        Map<String, Object> slots = new LinkedHashMap<>(task.slots());
        slots.put("orderNo", orderNo);
        List<String> missing = task.missingSlots().stream()
                .filter(name -> !"orderNo".equals(name))
                .toList();
        return new AssistantTask(
                task.taskId(), task.intent(), task.action(), task.executionMode(), slots,
                missing, task.dependsOn(), task.conditions(), task.confidence(), task.reason());
    }

    private void synchronizeTaskDefinitions(AssistantPlanRun planRun, AssistantPlan plan) {
        Map<String, AssistantTask> definitions = plan.tasks().stream()
                .collect(java.util.stream.Collectors.toMap(AssistantTask::taskId, value -> value));
        for (AssistantTaskRun run : taskRunRepository.findByPlanRunOrderByTaskOrderAsc(planRun)) {
            AssistantTask definition = definitions.get(run.getTaskId());
            if (definition != null) {
                run.setTaskJson(codec.write(definition));
                taskRunRepository.save(run);
            }
        }
    }

    private boolean isSuccessfulAnswerOnly(AssistantTask task, TaskToolResult result) {
        return result.status() == ToolExecutionStatus.NOT_SUPPORTED
                && (task.executionMode() == ExecutionMode.ANSWER_ONLY
                || task.executionMode() == ExecutionMode.CLARIFY);
    }

    private void saveTaskResult(AssistantTaskRun taskRun,
                                AgentRunStatus status,
                                TaskToolResult result,
                                boolean completed) {
        taskRun.setStatus(status);
        taskRun.setToolName(result.toolName());
        taskRun.setTargetRef(result.targetRef());
        taskRun.setResultMessage(clip(result.message(), 1000));
        taskRun.setResultJson(codec.write(result));
        if (taskRun.getStartedAt() == null) {
            taskRun.setStartedAt(Instant.now());
        }
        if (completed) {
            taskRun.setCompletedAt(Instant.now());
        }
    }

    private TaskToolResult result(AssistantTask task,
                                  ToolExecutionStatus status,
                                  String targetRef,
                                  String toolName,
                                  Map<String, Object> data,
                                  String message) {
        return new TaskToolResult(
                task.taskId(), task.action(), toolName, status, task.slots(), targetRef, data, message);
    }

    private void waitFor(AssistantPlanRun planRun,
                         String taskId,
                         AgentRunStatus status,
                         Instant expiresAt) {
        planRun.setStatus(status);
        planRun.setCurrentTaskId(taskId);
        planRun.setExpiresAt(expiresAt);
        planRunRepository.save(planRun);
    }

    private void clearWait(AssistantPlanRun planRun) {
        planRun.setCurrentTaskId(null);
        planRun.setExpiresAt(null);
    }

    private void completePlan(AssistantPlanRun planRun) {
        clearWait(planRun);
        planRun.setStatus(AgentRunStatus.SUCCEEDED);
        planRun.setCompletedAt(Instant.now());
        planRunRepository.save(planRun);
    }

    private void failPlan(AssistantPlanRun planRun, String reason) {
        clearWait(planRun);
        planRun.setStatus(AgentRunStatus.FAILED);
        planRun.setFailureReason(clip(reason, 1000));
        planRun.setCompletedAt(Instant.now());
        planRunRepository.save(planRun);
    }

    private boolean isExpired(AssistantPlanRun run) {
        return run.getExpiresAt() != null && !run.getExpiresAt().isAfter(Instant.now());
    }

    private String safeMessage(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank() ? "确认动作执行失败" : ex.getMessage();
    }

    private String clip(String value, int maximum) {
        if (value == null || value.length() <= maximum) {
            return value;
        }
        return value.substring(0, maximum);
    }
}

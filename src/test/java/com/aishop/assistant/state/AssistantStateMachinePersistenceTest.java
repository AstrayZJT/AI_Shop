package com.aishop.assistant.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.guardrail.ActionAuditService;
import com.aishop.assistant.guardrail.ActionAuditEvent;
import com.aishop.assistant.guardrail.AgentAccessDeniedException;
import com.aishop.assistant.guardrail.AgentConflictException;
import com.aishop.assistant.guardrail.ActionPolicyRegistry;
import com.aishop.assistant.model.AssistantIntent;
import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.AssistantTask;
import com.aishop.assistant.model.ConditionField;
import com.aishop.assistant.model.ConditionOperator;
import com.aishop.assistant.model.ExecutionMode;
import com.aishop.assistant.model.PlanType;
import com.aishop.assistant.model.PlannerResult;
import com.aishop.assistant.model.PlannerSource;
import com.aishop.assistant.model.TaskCondition;
import com.aishop.assistant.orchestration.AssistantToolOrchestrator;
import com.aishop.assistant.tool.TaskToolResult;
import com.aishop.assistant.tool.ToolExecutionOutcome;
import com.aishop.assistant.tool.ToolExecutionStatus;
import com.aishop.assistant.validation.PlanValidator;
import com.aishop.config.AssistantStateProperties;
import com.aishop.domain.AppUser;
import com.aishop.domain.AssistantSession;
import com.aishop.domain.UserRole;
import com.aishop.repository.AppUserRepository;
import com.aishop.repository.AssistantActionAuditRepository;
import com.aishop.repository.AssistantPlanRunRepository;
import com.aishop.repository.AssistantSessionRepository;
import com.aishop.repository.AssistantTaskRunRepository;
import com.aishop.repository.PendingAssistantActionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AssistantStateMachinePersistenceTest {

    @Autowired
    private AssistantPlanRunRepository planRuns;

    @Autowired
    private AssistantTaskRunRepository taskRuns;

    @Autowired
    private PendingAssistantActionRepository pendingActions;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private AssistantSessionRepository sessions;

    @Autowired
    private AssistantActionAuditRepository actionAudits;

    @Autowired
    private TestEntityManager entityManager;

    private AppUser user;
    private AssistantSession session;
    private AssistantToolOrchestrator tools;
    private ConfirmedActionExecutor confirmedExecutor;

    @BeforeEach
    void setUp() {
        user = new AppUser();
        user.setUsername("state-user");
        user.setPasswordHash("test-hash");
        user.setDisplayName("状态机用户");
        user.setRole(UserRole.CUSTOMER);
        user = users.saveAndFlush(user);

        session = new AssistantSession();
        session.setUser(user);
        session.setTitle("状态机测试");
        session.setSummary("新会话");
        session.setLastIntent("unknown");
        session.setServiceStatus("ACTIVE");
        session = sessions.saveAndFlush(session);

        tools = mock(AssistantToolOrchestrator.class);
        confirmedExecutor = mock(ConfirmedActionExecutor.class);
    }

    @Test
    void persistsInputAndConfirmationWaitThenResumesAfterPersistenceContextIsCleared() {
        AssistantStateMachine firstProcess = stateMachine();
        PlannerResult planner = planner(List.of(cancelTask(Map.of(), List.of("orderNo"), List.of(), List.of())));
        when(tools.executeTask(any(), any())).thenAnswer(invocation -> {
            AssistantTask task = invocation.getArgument(1);
            return new TaskToolResult(
                    task.taskId(), task.action(), "prepare_cancel_order", ToolExecutionStatus.PREPARED,
                    task.slots(), String.valueOf(task.slots().get("orderNo")),
                    Map.of("requiresConfirmation", true, "currentStatus", "PENDING_PAYMENT"),
                    "高风险动作仅完成准备");
        });

        StateMachineExecution waitingInput = firstProcess.start(user, session, planner);
        assertThat(waitingInput.status()).isEqualTo(AgentRunStatus.WAITING_INPUT);

        StateMachineExecution waitingConfirmation = firstProcess.resume(
                user, session, "订单号是 ORD-12345678");
        assertThat(waitingConfirmation.status()).isEqualTo(AgentRunStatus.WAITING_CONFIRMATION);
        assertThat(waitingConfirmation.pendingActionId()).isNotNull();
        assertThat(pendingActions.count()).isEqualTo(1);

        entityManager.flush();
        entityManager.clear();
        user = users.findByUsername("state-user").orElseThrow();
        session = sessions.findById(session.getId()).orElseThrow();
        AssistantStateMachine restartedProcess = stateMachine();
        assertThat(restartedProcess.findWaiting(user, session))
                .get().extracting(WaitingAgentRun::status)
                .isEqualTo(AgentRunStatus.WAITING_CONFIRMATION);
        when(confirmedExecutor.execute(any(), any(), any())).thenReturn(
                new ToolExecutionOutcome(
                        Map.of("order", Map.of("orderNo", "ORD-12345678", "status", "CANCELLED")),
                        "订单取消成功"));

        StateMachineExecution completed = restartedProcess.resume(user, session, "确认执行");

        assertThat(completed.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(completed.resumed()).isTrue();
        assertThat(completed.execution().taskResults()).extracting(TaskToolResult::status)
                .containsExactly(ToolExecutionStatus.SUCCEEDED);
        assertThat(pendingActions.findById(waitingConfirmation.pendingActionId()).orElseThrow().getStatus())
                .isEqualTo(PendingActionStatus.EXECUTED);
        verify(confirmedExecutor).execute(any(), any(), any());
    }

    @Test
    void skipsCancelWhenLogisticsShowsOrderAlreadyShipped() {
        AssistantTask query = new AssistantTask(
                "t1", AssistantIntent.ORDER, AssistantAction.QUERY_LOGISTICS, ExecutionMode.TOOL_READ,
                Map.of("orderNo", "ORD-12345678"), List.of(), List.of(), List.of(), 0.9, "query");
        TaskCondition unshipped = new TaskCondition(
                "t1", ConditionField.ORDER_STATUS, ConditionOperator.IN,
                List.of("PENDING_PAYMENT", "CONFIRMED", "PROCESSING"));
        AssistantTask cancel = cancelTask(
                Map.of("orderNo", "ORD-12345678"), List.of(), List.of("t1"), List.of(unshipped));
        when(tools.executeTask(any(), any())).thenReturn(new TaskToolResult(
                "t1", AssistantAction.QUERY_LOGISTICS, "query_logistics", ToolExecutionStatus.SUCCEEDED,
                query.slots(), "ORD-12345678",
                Map.of("logistics", Map.of("status", "SHIPPED")), "物流查询成功"));

        StateMachineExecution execution = stateMachine().start(user, session, planner(List.of(query, cancel)));

        assertThat(execution.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(execution.execution().taskResults()).extracting(TaskToolResult::status)
                .containsExactly(ToolExecutionStatus.SUCCEEDED, ToolExecutionStatus.SKIPPED_DEPENDENCY);
        assertThat(pendingActions.count()).isZero();
        verify(confirmedExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void persistsFailedPlanBeforeAnyToolRunsWhenTasksConflict() {
        AssistantTask cancel = new AssistantTask(
                "t1", AssistantIntent.ORDER, AssistantAction.CANCEL_ORDER, ExecutionMode.ASK_CONFIRM,
                Map.of("orderNo", "ORD-12345678"), List.of(), List.of(), List.of(), 0.9, "cancel");
        AssistantTask pay = new AssistantTask(
                "t2", AssistantIntent.ORDER, AssistantAction.PAY_ORDER, ExecutionMode.ASK_CONFIRM,
                Map.of("orderNo", "ORD-12345678"), List.of(), List.of(), List.of(), 0.9, "pay");

        StateMachineExecution execution = stateMachine().start(user, session, planner(List.of(cancel, pay)));

        assertThat(execution.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(execution.execution().taskResults()).allMatch(
                result -> result.status() == ToolExecutionStatus.FAILED);
        assertThat(planRuns.findById(execution.planRunId()).orElseThrow().getFailureReason())
                .contains("互斥写操作");
        verify(tools, never()).executeTask(any(), any());
    }

    @Test
    void rejectsPendingActionWithoutExecutingBusinessChange() {
        StateMachineExecution waiting = startWaitingConfirmation();

        StateMachineExecution rejected = stateMachine().resume(user, session, "取消操作");

        assertThat(rejected.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(rejected.execution().taskResults()).extracting(TaskToolResult::status)
                .containsExactly(ToolExecutionStatus.REJECTED);
        assertThat(pendingActions.findById(waiting.pendingActionId()).orElseThrow().getStatus())
                .isEqualTo(PendingActionStatus.REJECTED);
        verify(confirmedExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void expiresWaitingPlanBeforeConfirmationCanExecute() {
        StateMachineExecution waiting = startWaitingConfirmation();
        var planRun = planRuns.findById(waiting.planRunId()).orElseThrow();
        planRun.setExpiresAt(Instant.now().minusSeconds(1));
        planRuns.saveAndFlush(planRun);
        var pending = pendingActions.findById(waiting.pendingActionId()).orElseThrow();
        pending.setExpiresAt(Instant.now().minusSeconds(1));
        pendingActions.saveAndFlush(pending);

        StateMachineExecution expired = stateMachine().resume(user, session, "确认执行");

        assertThat(expired.status()).isEqualTo(AgentRunStatus.EXPIRED);
        assertThat(expired.execution().taskResults()).extracting(TaskToolResult::status)
                .containsExactly(ToolExecutionStatus.EXPIRED);
        assertThat(pendingActions.findById(waiting.pendingActionId()).orElseThrow().getStatus())
                .isEqualTo(PendingActionStatus.EXPIRED);
        verify(confirmedExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void explicitConfirmationIsIdempotentForSameClientRequestId() {
        StateMachineExecution waiting = startWaitingConfirmation();
        when(confirmedExecutor.execute(any(), any(), any())).thenReturn(
                new ToolExecutionOutcome(
                        Map.of("order", Map.of("orderNo", "ORD-12345678", "status", "CANCELLED")),
                        "订单取消成功"));

        StateMachineExecution first = stateMachine().confirmPendingAction(
                user, session.getId(), waiting.pendingActionId(), "request-12345678");
        StateMachineExecution replay = stateMachine().confirmPendingAction(
                user, session.getId(), waiting.pendingActionId(), "request-12345678");

        assertThat(first.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(first.idempotentReplay()).isFalse();
        assertThat(replay.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
        assertThat(replay.idempotentReplay()).isTrue();
        verify(confirmedExecutor).execute(any(), any(), any());
        var pending = pendingActions.findById(waiting.pendingActionId()).orElseThrow();
        assertThat(pending.getClientRequestId()).isEqualTo("request-12345678");
        assertThat(pending.getResultJson()).contains("SUCCEEDED", "ORD-12345678");
        assertThat(actionAudits.findByPendingActionOrderByCreatedAtAsc(pending))
                .extracting(audit -> audit.getEvent())
                .containsExactly(
                        ActionAuditEvent.PREPARED,
                        ActionAuditEvent.CONFIRMED,
                        ActionAuditEvent.EXECUTED,
                        ActionAuditEvent.IDEMPOTENT_REPLAY);
    }

    @Test
    void rejectsSecondConfirmationWithDifferentRequestId() {
        StateMachineExecution waiting = startWaitingConfirmation();
        when(confirmedExecutor.execute(any(), any(), any())).thenReturn(
                new ToolExecutionOutcome(Map.of("status", "CANCELLED"), "订单取消成功"));
        AssistantStateMachine machine = stateMachine();
        machine.confirmPendingAction(
                user, session.getId(), waiting.pendingActionId(), "request-original");

        assertThatThrownBy(() -> machine.confirmPendingAction(
                user, session.getId(), waiting.pendingActionId(), "request-another"))
                .isInstanceOf(AgentConflictException.class);

        verify(confirmedExecutor).execute(any(), any(), any());
    }

    @Test
    void persistsBusinessRejectionAsFailedExecution() {
        StateMachineExecution waiting = startWaitingConfirmation();
        when(confirmedExecutor.execute(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("当前订单状态不支持取消"));

        StateMachineExecution failed = stateMachine().confirmPendingAction(
                user, session.getId(), waiting.pendingActionId(), "request-status-changed");

        assertThat(failed.status()).isEqualTo(AgentRunStatus.FAILED);
        var pending = pendingActions.findById(waiting.pendingActionId()).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(PendingActionStatus.FAILED);
        assertThat(pending.getResultMessage()).contains("当前订单状态不支持取消");
        assertThat(actionAudits.findByPendingActionOrderByCreatedAtAsc(pending))
                .extracting(audit -> audit.getEvent())
                .containsExactly(
                        ActionAuditEvent.PREPARED,
                        ActionAuditEvent.CONFIRMED,
                        ActionAuditEvent.FAILED);
    }

    @Test
    void blocksCrossUserAndSessionTamperingBeforeExecution() {
        StateMachineExecution waiting = startWaitingConfirmation();
        AppUser attacker = new AppUser();
        attacker.setUsername("state-attacker");
        attacker.setPasswordHash("test-hash");
        attacker.setDisplayName("越权测试用户");
        attacker.setRole(UserRole.CUSTOMER);
        attacker = users.saveAndFlush(attacker);
        AssistantStateMachine machine = stateMachine();
        AppUser otherUser = attacker;

        assertThatThrownBy(() -> machine.confirmPendingAction(
                otherUser, session.getId(), waiting.pendingActionId(), "request-attacker"))
                .isInstanceOf(AgentAccessDeniedException.class);
        assertThatThrownBy(() -> machine.confirmPendingAction(
                user, session.getId() + 999, waiting.pendingActionId(), "request-session"))
                .isInstanceOf(AgentAccessDeniedException.class);

        var pending = pendingActions.findById(waiting.pendingActionId()).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(PendingActionStatus.PENDING);
        assertThat(actionAudits.findByPendingActionOrderByCreatedAtAsc(pending))
                .extracting(audit -> audit.getEvent())
                .containsExactly(
                        ActionAuditEvent.PREPARED,
                        ActionAuditEvent.DENIED,
                        ActionAuditEvent.DENIED);
        verify(confirmedExecutor, never()).execute(any(), any(), any());
    }

    private StateMachineExecution startWaitingConfirmation() {
        AssistantTask cancel = cancelTask(
                Map.of("orderNo", "ORD-12345678"), List.of(), List.of(), List.of());
        when(tools.executeTask(any(), any())).thenReturn(new TaskToolResult(
                cancel.taskId(), cancel.action(), "prepare_cancel_order", ToolExecutionStatus.PREPARED,
                cancel.slots(), "ORD-12345678",
                Map.of("requiresConfirmation", true, "currentStatus", "PENDING_PAYMENT"),
                "高风险动作仅完成准备"));
        StateMachineExecution waiting = stateMachine().start(user, session, planner(List.of(cancel)));
        assertThat(waiting.status()).isEqualTo(AgentRunStatus.WAITING_CONFIRMATION);
        return waiting;
    }

    private AssistantStateMachine stateMachine() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AgentStateCodec codec = new AgentStateCodec(objectMapper);
        return new AssistantStateMachine(
                planRuns,
                taskRuns,
                pendingActions,
                tools,
                new TaskSorter(),
                new TaskConflictAnalyzer(),
                new ConditionEvaluator(objectMapper),
                new PendingInputResolver(),
                new ConfirmationDecisionResolver(),
                confirmedExecutor,
                codec,
                new PlanValidator(),
                new AssistantStateProperties(Duration.ofMinutes(30), Duration.ofMinutes(10)),
                new ActionPolicyRegistry(),
                new ActionAuditService(actionAudits));
    }

    private AssistantTask cancelTask(Map<String, Object> slots,
                                     List<String> missing,
                                     List<String> dependsOn,
                                     List<TaskCondition> conditions) {
        return new AssistantTask(
                "t2", AssistantIntent.ORDER, AssistantAction.CANCEL_ORDER, ExecutionMode.ASK_CONFIRM,
                slots, missing, dependsOn, conditions, 0.9, "cancel");
    }

    private PlannerResult planner(List<AssistantTask> tasks) {
        PlanType type = tasks.size() == 1 ? PlanType.SINGLE_TASK : PlanType.MULTI_TASK;
        return new PlannerResult(
                new AssistantPlan(type, tasks, "状态机测试计划"),
                PlannerSource.LLM, "test", null, null, "test-model", null, null);
    }
}

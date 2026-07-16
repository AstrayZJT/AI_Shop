package com.aishop.assistant.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.aishop.assistant.guardrail.ActionAuditEvent;
import com.aishop.assistant.guardrail.ActionAuditService;
import com.aishop.assistant.guardrail.ActionPolicyRegistry;
import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.model.AssistantIntent;
import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.AssistantTask;
import com.aishop.assistant.model.ExecutionMode;
import com.aishop.assistant.model.PlanType;
import com.aishop.assistant.model.PlannerResult;
import com.aishop.assistant.model.PlannerSource;
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
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PendingActionConcurrencyTest {

    @Autowired private AssistantPlanRunRepository planRuns;
    @Autowired private AssistantTaskRunRepository taskRuns;
    @Autowired private PendingAssistantActionRepository pendingActions;
    @Autowired private AssistantActionAuditRepository actionAudits;
    @Autowired private AppUserRepository users;
    @Autowired private AssistantSessionRepository sessions;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void concurrentSameRequestExecutesBusinessActionOnlyOnce() throws Exception {
        AssistantToolOrchestrator tools = mock(AssistantToolOrchestrator.class);
        ConfirmedActionExecutor executor = mock(ConfirmedActionExecutor.class);
        AssistantStateMachine machine = stateMachine(tools, executor);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Long[] ids = tx.execute(status -> {
            AppUser user = new AppUser();
            user.setUsername("concurrent-user");
            user.setPasswordHash("test-hash");
            user.setDisplayName("并发测试用户");
            user.setRole(UserRole.CUSTOMER);
            user = users.save(user);
            AssistantSession session = new AssistantSession();
            session.setUser(user);
            session.setTitle("并发确认测试");
            session.setSummary("新会话");
            session.setLastIntent("unknown");
            session.setServiceStatus("ACTIVE");
            session = sessions.save(session);
            return new Long[] {user.getId(), session.getId()};
        });

        AssistantTask task = new AssistantTask(
                "t1", AssistantIntent.ORDER, AssistantAction.CANCEL_ORDER, ExecutionMode.ASK_CONFIRM,
                Map.of("orderNo", "ORD-12345678"), List.of(), List.of(), List.of(), 0.95, "取消订单");
        when(tools.executeTask(any(), any())).thenReturn(new TaskToolResult(
                "t1", AssistantAction.CANCEL_ORDER, "prepare_cancel_order", ToolExecutionStatus.PREPARED,
                task.slots(), "ORD-12345678", Map.of("currentStatus", "PENDING_PAYMENT"),
                "等待确认"));
        Long pendingId = tx.execute(status -> {
            AppUser user = users.findById(ids[0]).orElseThrow();
            AssistantSession session = sessions.findById(ids[1]).orElseThrow();
            PlannerResult planner = new PlannerResult(
                    new AssistantPlan(PlanType.SINGLE_TASK, List.of(task), "取消订单"),
                    PlannerSource.LLM, "test", null, null, "test-model", null, null);
            return machine.start(user, session, planner).pendingActionId();
        });

        CountDownLatch executing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(executor.execute(any(), any(), any())).thenAnswer(invocation -> {
            executing.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return new ToolExecutionOutcome(Map.of("status", "CANCELLED"), "订单取消成功");
        });

        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> tx.execute(status -> confirm(
                    machine, ids[0], ids[1], pendingId, "concurrent-request")));
            assertThat(executing.await(5, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> tx.execute(status -> confirm(
                    machine, ids[0], ids[1], pendingId, "concurrent-request")));
            Thread.sleep(200);
            assertThat(second.isDone()).isFalse();
            release.countDown();

            StateMachineExecution firstResult = first.get(5, TimeUnit.SECONDS);
            StateMachineExecution secondResult = second.get(5, TimeUnit.SECONDS);
            assertThat(List.of(firstResult.idempotentReplay(), secondResult.idempotentReplay()))
                    .containsExactlyInAnyOrder(false, true);
        }

        verify(executor).execute(any(), any(), any());
        var pending = pendingActions.findById(pendingId).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(PendingActionStatus.EXECUTED);
        assertThat(actionAudits.findByPendingActionOrderByCreatedAtAsc(pending))
                .extracting(audit -> audit.getEvent())
                .containsExactly(
                        ActionAuditEvent.PREPARED,
                        ActionAuditEvent.CONFIRMED,
                        ActionAuditEvent.EXECUTED,
                        ActionAuditEvent.IDEMPOTENT_REPLAY);
    }

    private StateMachineExecution confirm(AssistantStateMachine machine,
                                          Long userId,
                                          Long sessionId,
                                          Long pendingId,
                                          String requestId) {
        return machine.confirmPendingAction(
                users.findById(userId).orElseThrow(), sessionId, pendingId, requestId);
    }

    private AssistantStateMachine stateMachine(AssistantToolOrchestrator tools,
                                               ConfirmedActionExecutor executor) {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        return new AssistantStateMachine(
                planRuns, taskRuns, pendingActions, tools,
                new TaskSorter(), new TaskConflictAnalyzer(), new ConditionEvaluator(mapper),
                new PendingInputResolver(), new ConfirmationDecisionResolver(), executor,
                new AgentStateCodec(mapper), new PlanValidator(),
                new AssistantStateProperties(Duration.ofMinutes(30), Duration.ofMinutes(10)),
                new ActionPolicyRegistry(), new ActionAuditService(actionAudits));
    }
}

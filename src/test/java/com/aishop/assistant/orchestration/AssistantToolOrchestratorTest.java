package com.aishop.assistant.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.model.AssistantIntent;
import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.AssistantTask;
import com.aishop.assistant.model.ExecutionMode;
import com.aishop.assistant.model.PlanType;
import com.aishop.assistant.model.PlannerResult;
import com.aishop.assistant.model.PlannerSource;
import com.aishop.assistant.tool.AssistantTool;
import com.aishop.assistant.tool.AssistantToolRegistry;
import com.aishop.assistant.tool.PreparedToolCall;
import com.aishop.assistant.tool.ToolContext;
import com.aishop.assistant.tool.ToolExecutionOutcome;
import com.aishop.assistant.tool.ToolExecutionStatus;
import com.aishop.assistant.tool.ToolPolicy;
import com.aishop.assistant.tool.ToolRiskLevel;
import com.aishop.assistant.state.TaskSorter;
import com.aishop.domain.AppUser;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

class AssistantToolOrchestratorTest {

    @Test
    void executesReadOnlyTool() {
        CountingTool tool = new CountingTool(AssistantAction.QUERY_ORDER, ToolRiskLevel.READ_ONLY, true);
        AssistantToolOrchestrator orchestrator = orchestrator(tool);

        var result = orchestrator.executePlan(user(), planner(List.of(
                task("t1", AssistantAction.QUERY_ORDER, ExecutionMode.TOOL_READ, Map.of("orderNo", "ORD-1"), List.of(), List.of()))));

        assertThat(result.taskResults().getFirst().status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
        assertThat(tool.executeCount).hasValue(1);
    }

    @Test
    void preparesHighRiskToolWithoutCallingExecute() {
        CountingTool tool = new CountingTool(AssistantAction.CANCEL_ORDER, ToolRiskLevel.PREPARE_ONLY, false);
        AssistantToolOrchestrator orchestrator = orchestrator(tool);

        var result = orchestrator.executePlan(user(), planner(List.of(
                task("t1", AssistantAction.CANCEL_ORDER, ExecutionMode.ASK_CONFIRM, Map.of("orderNo", "ORD-1"), List.of(), List.of()))));

        assertThat(result.taskResults().getFirst().status()).isEqualTo(ToolExecutionStatus.PREPARED);
        assertThat(tool.executeCount).hasValue(0);
    }

    @Test
    void reportsMissingInputWithoutPreparingTool() {
        CountingTool tool = new CountingTool(AssistantAction.CANCEL_ORDER, ToolRiskLevel.PREPARE_ONLY, false);
        AssistantToolOrchestrator orchestrator = orchestrator(tool);

        var result = orchestrator.executePlan(user(), planner(List.of(
                task("t1", AssistantAction.CANCEL_ORDER, ExecutionMode.ASK_CONFIRM, Map.of(), List.of("orderNo"), List.of()))));

        assertThat(result.taskResults().getFirst().status()).isEqualTo(ToolExecutionStatus.NEEDS_INPUT);
        assertThat(tool.prepareCount).hasValue(0);
    }

    @Test
    void skipsDependentTaskWhenDependencyFails() {
        CountingTool tool = new CountingTool(AssistantAction.QUERY_ORDER, ToolRiskLevel.READ_ONLY, true);
        AssistantToolOrchestrator orchestrator = orchestrator(tool);
        AssistantTask failed = task(
                "t1", AssistantAction.QUERY_ORDER, ExecutionMode.TOOL_READ, Map.of("fail", true), List.of(), List.of());
        AssistantTask dependent = task(
                "t2", AssistantAction.QUERY_ORDER, ExecutionMode.TOOL_READ, Map.of(), List.of(), List.of("t1"));

        var result = orchestrator.executePlan(user(), planner(List.of(dependent, failed)));

        assertThat(result.taskResults()).extracting(item -> item.status())
                .containsExactly(ToolExecutionStatus.FAILED, ToolExecutionStatus.SKIPPED_DEPENDENCY);
    }

    @Test
    void reportsUnsupportedAction() {
        AssistantToolOrchestrator orchestrator = new AssistantToolOrchestrator(
                new AssistantToolRegistry(List.of()), new TaskSorter());

        var result = orchestrator.executePlan(user(), planner(List.of(
                task("t1", AssistantAction.GENERAL_CHAT, ExecutionMode.ANSWER_ONLY, Map.of(), List.of(), List.of()))));

        assertThat(result.taskResults().getFirst().status()).isEqualTo(ToolExecutionStatus.NOT_SUPPORTED);
    }

    private AssistantToolOrchestrator orchestrator(AssistantTool tool) {
        return new AssistantToolOrchestrator(new AssistantToolRegistry(List.of(tool)), new TaskSorter());
    }

    private PlannerResult planner(List<AssistantTask> tasks) {
        PlanType type = tasks.size() == 1 ? PlanType.SINGLE_TASK : PlanType.MULTI_TASK;
        return new PlannerResult(
                new AssistantPlan(type, tasks, "test"),
                PlannerSource.LLM,
                "test",
                null,
                null,
                "fake",
                null,
                null);
    }

    private AssistantTask task(String id,
                               AssistantAction action,
                               ExecutionMode mode,
                               Map<String, Object> slots,
                               List<String> missing,
                               List<String> dependencies) {
        AssistantIntent intent = action == AssistantAction.GENERAL_CHAT
                ? AssistantIntent.CHAT
                : AssistantIntent.ORDER;
        return new AssistantTask(
                id, intent, action, mode, slots, missing, dependencies, List.of(), 0.9, "test");
    }

    private AppUser user() {
        AppUser user = new AppUser();
        user.setId(1L);
        return user;
    }

    private static final class CountingTool implements AssistantTool {
        private final ToolPolicy policy;
        private final ToolSpecification specification;
        private final AtomicInteger prepareCount = new AtomicInteger();
        private final AtomicInteger executeCount = new AtomicInteger();

        private CountingTool(AssistantAction action, ToolRiskLevel risk, boolean autoExecutable) {
            this.policy = new ToolPolicy(
                    "tool_" + action.name().toLowerCase(), "test tool", action, risk, autoExecutable);
            this.specification = ToolSpecification.builder()
                    .name(policy.name())
                    .description(policy.description())
                    .parameters(JsonObjectSchema.builder().additionalProperties(true).build())
                    .build();
        }

        @Override
        public ToolPolicy policy() {
            return policy;
        }

        @Override
        public ToolSpecification specification() {
            return specification;
        }

        @Override
        public PreparedToolCall prepare(ToolContext context, Map<String, Object> arguments) {
            prepareCount.incrementAndGet();
            return new PreparedToolCall(
                    policy.action(), policy.name(), policy.riskLevel(), arguments, "target", Map.of("prepared", true));
        }

        @Override
        public ToolExecutionOutcome execute(ToolContext context, PreparedToolCall call) {
            executeCount.incrementAndGet();
            if (Boolean.TRUE.equals(call.arguments().get("fail"))) {
                throw new IllegalArgumentException("forced failure");
            }
            return new ToolExecutionOutcome(Map.of("ok", true), "ok");
        }
    }
}

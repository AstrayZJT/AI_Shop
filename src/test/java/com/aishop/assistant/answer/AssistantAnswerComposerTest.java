package com.aishop.assistant.answer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.aishop.assistant.context.AssistantContext;
import com.aishop.assistant.context.AuthoritativeOrderFact;
import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.model.AssistantIntent;
import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.AssistantTask;
import com.aishop.assistant.model.ExecutionMode;
import com.aishop.assistant.model.PlanType;
import com.aishop.assistant.model.PlannerResult;
import com.aishop.assistant.model.PlannerSource;
import com.aishop.assistant.orchestration.ToolPlanExecutionResult;
import com.aishop.assistant.tool.TaskToolResult;
import com.aishop.assistant.tool.ToolExecutionStatus;
import com.aishop.config.AssistantContextProperties;
import com.aishop.config.ShopProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

class AssistantAnswerComposerTest {

    @Test
    void preparedActionNeverClaimsBusinessChangeWasExecuted() {
        AssistantAnswerComposer composer = composer(false, prompt -> "unused");
        TaskToolResult prepared = new TaskToolResult(
                "t1", AssistantAction.CANCEL_ORDER, "prepare_cancel_order", ToolExecutionStatus.PREPARED,
                Map.of("orderNo", "ORD-12345678"), "ORD-12345678",
                Map.of("effect", "确认后订单状态将变更为 CANCELLED"), "prepared");

        AssistantComposedAnswer answer = composer.compose(
                context("取消订单"), execution(prepared), null);

        assertThat(answer.answer()).contains("需要二次确认", "尚未执行");
        assertThat(answer.answer()).doesNotContain("已经取消", "取消成功");
    }

    @Test
    void missingSlotProducesClarificationInsteadOfCallingModel() {
        AtomicReference<String> prompt = new AtomicReference<>();
        AssistantAnswerComposer composer = composer(true, value -> {
            prompt.set(value);
            return "unused";
        });
        TaskToolResult missing = new TaskToolResult(
                "t1", AssistantAction.QUERY_ORDER, null, ToolExecutionStatus.NEEDS_INPUT,
                Map.of(), null, Map.of("missingSlots", List.of("orderNo")), "missing");

        AssistantComposedAnswer answer = composer.compose(context("查询订单"), execution(missing), null);

        assertThat(answer.answer()).contains("请补充订单号");
        assertThat(prompt).hasNullValue();
    }

    @Test
    void generalChatUsesBoundedContextPrompt() {
        AtomicReference<String> captured = new AtomicReference<>();
        AssistantAnswerComposer composer = composer(true, prompt -> {
            captured.set(prompt);
            return "你好，我可以帮你查询订单或商品。";
        });
        TaskToolResult unsupported = new TaskToolResult(
                "t1", AssistantAction.GENERAL_CHAT, null, ToolExecutionStatus.NOT_SUPPORTED,
                Map.of(), null, Map.of(), "not supported");

        AssistantComposedAnswer answer = composer.compose(context("你好"), execution(unsupported), null);

        assertThat(answer.mode()).isEqualTo("MODEL_CHAT");
        assertThat(captured.get()).contains("authoritativeOrders", "不能用来证明订单归属");
        assertThat(captured.get().length()).isLessThan(3_000);
    }

    @Test
    void structuredAnswerIsCappedToMessageColumnLimit() {
        AssistantAnswerComposer composer = composer(false, prompt -> "unused");
        TaskToolResult failed = new TaskToolResult(
                "t1", AssistantAction.QUERY_ORDER, "query_order", ToolExecutionStatus.FAILED,
                Map.of(), null, Map.of(), "失败原因".repeat(2_000));

        AssistantComposedAnswer answer = composer.compose(context("查询订单"), execution(failed), null);

        assertThat(answer.answer().length()).isLessThanOrEqualTo(3_000);
    }

    private AssistantAnswerComposer composer(boolean enabled, AssistantAnswerModelGateway gateway) {
        ShopProperties properties = new ShopProperties(
                new ShopProperties.Ai(
                        enabled, "https://example.test/v1", enabled ? "test-key" : "", "test-model",
                        "embedding", false, false, Duration.ofSeconds(5), 0, 1000, 0.0),
                new ShopProperties.Rag(4));
        return new AssistantAnswerComposer(
                gateway,
                properties,
                new AssistantContextProperties(2_000, 500, 500, 6, 500, 3),
                new ObjectMapper());
    }

    private AssistantContext context(String message) {
        return new AssistantContext(
                message,
                "只用于理解的历史摘要",
                List.of(),
                List.of(new AuthoritativeOrderFact("ORD-12345678", "SHIPPED", null, null, null)),
                null,
                null,
                100,
                2_000,
                false);
    }

    private ToolPlanExecutionResult execution(TaskToolResult result) {
        AssistantTask task = new AssistantTask(
                "t1", AssistantIntent.CHAT, result.action(), mode(result.action()),
                Map.of(), List.of(), List.of(), List.of(), 0.9, "test");
        PlannerResult planner = new PlannerResult(
                new AssistantPlan(PlanType.SINGLE_TASK, List.of(task), "test"),
                PlannerSource.LLM, "test", null, null, "model", null, null);
        return new ToolPlanExecutionResult(planner, List.of(result));
    }

    private ExecutionMode mode(AssistantAction action) {
        return switch (action) {
            case CANCEL_ORDER -> ExecutionMode.ASK_CONFIRM;
            case QUERY_ORDER -> ExecutionMode.TOOL_READ;
            default -> ExecutionMode.ANSWER_ONLY;
        };
    }
}

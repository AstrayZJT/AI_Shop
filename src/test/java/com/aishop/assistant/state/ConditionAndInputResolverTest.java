package com.aishop.assistant.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.model.AssistantIntent;
import com.aishop.assistant.model.AssistantTask;
import com.aishop.assistant.model.ConditionField;
import com.aishop.assistant.model.ConditionOperator;
import com.aishop.assistant.model.ExecutionMode;
import com.aishop.assistant.model.TaskCondition;
import com.aishop.assistant.tool.TaskToolResult;
import com.aishop.assistant.tool.ToolExecutionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

class ConditionAndInputResolverTest {

    @Test
    void evaluatesOnlyWhitelistedOrderStatusPath() {
        ConditionEvaluator evaluator = new ConditionEvaluator(new ObjectMapper());
        TaskCondition condition = new TaskCondition(
                "t1", ConditionField.ORDER_STATUS, ConditionOperator.IN,
                List.of("PENDING_PAYMENT", "CONFIRMED", "PROCESSING"));
        TaskToolResult source = new TaskToolResult(
                "t1", AssistantAction.QUERY_LOGISTICS, "query_logistics", ToolExecutionStatus.SUCCEEDED,
                Map.of(), "ORD-12345678",
                Map.of("logistics", Map.of("status", "SHIPPED")), "ok");

        ConditionEvaluation evaluation = evaluator.evaluate(List.of(condition), Map.of("t1", source));

        assertThat(evaluation.matched()).isFalse();
        assertThat(evaluation.reason()).contains("ORDER_STATUS");
    }

    @Test
    void fillsOrderNumberWithoutChangingOtherTaskFields() {
        AssistantTask task = new AssistantTask(
                "t1", AssistantIntent.ORDER, AssistantAction.CANCEL_ORDER, ExecutionMode.ASK_CONFIRM,
                Map.of(), List.of("orderNo"), List.of(), List.of(), 0.8, "test");

        AssistantTask resolved = new PendingInputResolver().resolve(task, "订单号是 ord-12345678");

        assertThat(resolved.slots()).containsEntry("orderNo", "ORD-12345678");
        assertThat(resolved.missingSlots()).isEmpty();
        assertThat(resolved.action()).isEqualTo(AssistantAction.CANCEL_ORDER);
    }

    @Test
    void confirmationRequiresExplicitShortDecision() {
        ConfirmationDecisionResolver resolver = new ConfirmationDecisionResolver();

        assertThat(resolver.resolve("确认执行")).isEqualTo(ConfirmationDecision.CONFIRM);
        assertThat(resolver.resolve("取消操作")).isEqualTo(ConfirmationDecision.REJECT);
        assertThat(resolver.resolve("我想确认一下取消规则")).isEqualTo(ConfirmationDecision.UNKNOWN);
    }
}

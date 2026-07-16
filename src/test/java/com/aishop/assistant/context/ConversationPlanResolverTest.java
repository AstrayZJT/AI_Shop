package com.aishop.assistant.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.model.AssistantIntent;
import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.AssistantTask;
import com.aishop.assistant.model.ExecutionMode;
import com.aishop.assistant.model.PlanType;
import com.aishop.assistant.model.PlannerResult;
import com.aishop.assistant.model.PlannerSource;
import com.aishop.assistant.validation.PlanValidator;

class ConversationPlanResolverTest {

    private final ConversationPlanResolver resolver = new ConversationPlanResolver(new PlanValidator());

    @Test
    void fillsMissingOrderNumberFromTrustedConversationReference() {
        AssistantContext context = context("取消这个订单", "ORD-12345678");

        PlannerResult resolved = resolver.resolve(context, planner(Map.of(), List.of("orderNo")));

        AssistantTask task = resolved.plan().tasks().getFirst();
        assertThat(task.slots()).containsEntry("orderNo", "ORD-12345678");
        assertThat(task.missingSlots()).doesNotContain("orderNo");
    }

    @Test
    void removesOrderNumberHallucinatedByModel() {
        AssistantContext context = context("帮我取消订单", null);

        PlannerResult resolved = resolver.resolve(
                context, planner(Map.of("orderNo", "ORD-87654321"), List.of()));

        AssistantTask task = resolved.plan().tasks().getFirst();
        assertThat(task.slots()).doesNotContainKey("orderNo");
        assertThat(task.missingSlots()).containsExactly("orderNo");
    }

    @Test
    void keepsOrderNumberExplicitlyProvidedByUser() {
        AssistantContext context = context("取消 ORD-12345678", null);

        PlannerResult resolved = resolver.resolve(
                context, planner(Map.of("orderNo", "ord-12345678"), List.of()));

        assertThat(resolved.plan().tasks().getFirst().slots())
                .containsEntry("orderNo", "ord-12345678");
    }

    private AssistantContext context(String message, String resolvedOrderNo) {
        return new AssistantContext(
                message, null, List.of(), List.of(), null, resolvedOrderNo, message.length(), 2_000, false);
    }

    private PlannerResult planner(Map<String, Object> slots, List<String> missing) {
        AssistantTask task = new AssistantTask(
                "t1", AssistantIntent.ORDER, AssistantAction.CANCEL_ORDER, ExecutionMode.ASK_CONFIRM,
                slots, missing, List.of(), List.of(), 0.9, "test");
        return new PlannerResult(
                new AssistantPlan(PlanType.SINGLE_TASK, List.of(task), "取消订单"),
                PlannerSource.LLM, "test", null, null, "test-model", null, null);
    }
}

package com.aishop.assistant.validation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.model.AssistantIntent;
import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.AssistantTask;
import com.aishop.assistant.model.ConditionField;
import com.aishop.assistant.model.ConditionOperator;
import com.aishop.assistant.model.ExecutionMode;
import com.aishop.assistant.model.PlanType;
import com.aishop.assistant.model.TaskCondition;

class PlanValidatorTest {

    private final PlanValidator validator = new PlanValidator();

    @Test
    void acceptsValidReadOnlyPlan() {
        validator.validate(single(queryTask("t1", List.of())));
    }

    @Test
    void rejectsMoreThanFiveTasks() {
        List<AssistantTask> tasks = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            tasks.add(queryTask("t" + i, List.of()));
        }

        assertInvalid(new AssistantPlan(PlanType.MULTI_TASK, tasks, "too many"), "不能超过 5 个");
    }

    @Test
    void rejectsDuplicateTaskIds() {
        var plan = new AssistantPlan(PlanType.MULTI_TASK,
                List.of(queryTask("t1", List.of()), queryTask("t1", List.of())), "duplicate");

        assertInvalid(plan, "taskId 重复");
    }

    @Test
    void rejectsMissingDependency() {
        var plan = single(queryTask("t1", List.of("missing")));

        assertInvalid(plan, "不存在的依赖");
    }

    @Test
    void rejectsCyclicDependencies() {
        var plan = new AssistantPlan(PlanType.MULTI_TASK,
                List.of(queryTask("t1", List.of("t2")), queryTask("t2", List.of("t1"))), "cycle");

        assertInvalid(plan, "依赖存在循环");
    }

    @Test
    void rejectsUnsafeExecutionModeForCancel() {
        var task = task(
                "t1", AssistantIntent.ORDER, AssistantAction.CANCEL_ORDER, ExecutionMode.TOOL_READ,
                Map.of("orderNo", "ORD-12345678"), List.of(), List.of(), List.of(), 0.9);

        assertInvalid(single(task), "executionMode 应为 ASK_CONFIRM");
    }

    @Test
    void rejectsUnknownSlot() {
        var task = task(
                "t1", AssistantIntent.ORDER, AssistantAction.QUERY_ORDER, ExecutionMode.TOOL_READ,
                Map.of("orderNo", "ORD-12345678", "admin", true), List.of(), List.of(), List.of(), 0.9);

        assertInvalid(single(task), "未允许字段: admin");
    }

    @Test
    void rejectsUndeclaredMissingRequiredSlot() {
        var task = task(
                "t1", AssistantIntent.ORDER, AssistantAction.QUERY_ORDER, ExecutionMode.TOOL_READ,
                Map.of(), List.of(), List.of(), List.of(), 0.9);

        assertInvalid(single(task), "缺少必填槽位声明: orderNo");
    }

    @Test
    void rejectsConditionWhoseSourceIsNotDependency() {
        var condition = new TaskCondition(
                "t1", ConditionField.ORDER_STATUS, ConditionOperator.EQ, List.of("CONFIRMED"));
        var second = task(
                "t2", AssistantIntent.ORDER, AssistantAction.CANCEL_ORDER, ExecutionMode.ASK_CONFIRM,
                Map.of("orderNo", "ORD-12345678"), List.of(), List.of(), List.of(condition), 0.9);
        var plan = new AssistantPlan(PlanType.MULTI_TASK, List.of(queryTask("t1", List.of()), second), "condition");

        assertInvalid(plan, "sourceTaskId 必须是当前任务的直接依赖");
    }

    @Test
    void rejectsUnknownOrderStatusInCondition() {
        var condition = new TaskCondition(
                "t1", ConditionField.ORDER_STATUS, ConditionOperator.EQ, List.of("NOT_A_STATUS"));
        var second = task(
                "t2", AssistantIntent.ORDER, AssistantAction.CANCEL_ORDER, ExecutionMode.ASK_CONFIRM,
                Map.of("orderNo", "ORD-12345678"), List.of(), List.of("t1"), List.of(condition), 0.9);
        var plan = new AssistantPlan(PlanType.MULTI_TASK, List.of(queryTask("t1", List.of()), second), "condition");

        assertInvalid(plan, "未知订单状态");
    }

    @Test
    void rejectsConditionWithOmittedExpectedValues() {
        var condition = new TaskCondition(
                "t1", ConditionField.ORDER_SHIPPED_AT, ConditionOperator.IS_NULL, null);
        var second = task(
                "t2", AssistantIntent.ORDER, AssistantAction.CANCEL_ORDER, ExecutionMode.ASK_CONFIRM,
                Map.of("orderNo", "ORD-12345678"), List.of(), List.of("t1"), List.of(condition), 0.9);
        var plan = new AssistantPlan(PlanType.MULTI_TASK, List.of(queryTask("t1", List.of()), second), "condition");

        assertInvalid(plan, "expectedValues 不能为空");
    }

    @Test
    void rejectsOutOfRangeConfidence() {
        var task = task(
                "t1", AssistantIntent.ORDER, AssistantAction.QUERY_ORDER, ExecutionMode.TOOL_READ,
                Map.of("orderNo", "ORD-12345678"), List.of(), List.of(), List.of(), 1.5);

        assertInvalid(single(task), "confidence 必须在 0 到 1 之间");
    }

    @Test
    void rejectsSingleTaskTypeWithMultipleTasks() {
        var plan = new AssistantPlan(PlanType.SINGLE_TASK,
                List.of(queryTask("t1", List.of()), queryTask("t2", List.of())), "wrong type");

        assertInvalid(plan, "SINGLE_TASK 必须且只能包含一个任务");
    }

    @Test
    void rejectsIntentActionMismatch() {
        var task = task(
                "t1", AssistantIntent.PRODUCT, AssistantAction.QUERY_ORDER, ExecutionMode.TOOL_READ,
                Map.of("orderNo", "ORD-12345678"), List.of(), List.of(), List.of(), 0.8);

        assertInvalid(single(task), "intent 与 action 不匹配");
    }

    private AssistantPlan single(AssistantTask task) {
        return new AssistantPlan(PlanType.SINGLE_TASK, List.of(task), "test");
    }

    private AssistantTask queryTask(String taskId, List<String> dependsOn) {
        return task(
                taskId, AssistantIntent.ORDER, AssistantAction.QUERY_ORDER, ExecutionMode.TOOL_READ,
                Map.of("orderNo", "ORD-12345678"), List.of(), dependsOn, List.of(), 0.9);
    }

    private AssistantTask task(String taskId,
                               AssistantIntent intent,
                               AssistantAction action,
                               ExecutionMode mode,
                               Map<String, Object> slots,
                               List<String> missingSlots,
                               List<String> dependsOn,
                               List<TaskCondition> conditions,
                               Double confidence) {
        return new AssistantTask(
                taskId, intent, action, mode, slots, missingSlots,
                dependsOn, conditions, confidence, "test");
    }

    private void assertInvalid(AssistantPlan plan, String expectedMessage) {
        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining(expectedMessage);
    }
}

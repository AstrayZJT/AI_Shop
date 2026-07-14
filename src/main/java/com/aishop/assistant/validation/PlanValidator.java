package com.aishop.assistant.validation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.model.AssistantIntent;
import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.AssistantTask;
import com.aishop.assistant.model.ConditionField;
import com.aishop.assistant.model.ConditionOperator;
import com.aishop.assistant.model.ExecutionMode;
import com.aishop.assistant.model.PlanType;
import com.aishop.assistant.model.TaskCondition;
import com.aishop.domain.OrderStatus;

@Component
public class PlanValidator {

    public static final int MAX_TASKS = 5;
    private static final int MAX_SUMMARY_LENGTH = 500;
    private static final int MAX_REASON_LENGTH = 500;
    private static final int MAX_SLOT_TEXT_LENGTH = 512;
    private static final Pattern TASK_ID_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,31}");

    private static final Map<AssistantAction, ExecutionMode> EXPECTED_MODES = expectedModes();
    private static final Map<AssistantAction, Set<String>> ALLOWED_SLOTS = allowedSlots();
    private static final Map<AssistantAction, Set<String>> REQUIRED_SLOTS = requiredSlots();
    private static final Map<AssistantAction, Set<AssistantIntent>> ALLOWED_INTENTS = allowedIntents();

    public AssistantPlan validate(AssistantPlan plan) {
        List<String> violations = new ArrayList<>();
        if (plan == null) {
            throw new PlanValidationException(List.of("plan 不能为空"));
        }
        if (plan.planType() == null) {
            violations.add("planType 不能为空");
        }
        if (plan.summary() != null && plan.summary().length() > MAX_SUMMARY_LENGTH) {
            violations.add("summary 超过 " + MAX_SUMMARY_LENGTH + " 字符");
        }
        if (plan.tasks() == null || plan.tasks().isEmpty()) {
            violations.add("tasks 不能为空");
        } else if (plan.tasks().size() > MAX_TASKS) {
            violations.add("tasks 不能超过 " + MAX_TASKS + " 个");
        }

        if (plan.tasks() != null && !plan.tasks().isEmpty()) {
            validatePlanType(plan, violations);
            validateTasks(plan.tasks(), violations);
            validateDependencies(plan.tasks(), violations);
        }

        if (!violations.isEmpty()) {
            throw new PlanValidationException(violations);
        }
        return plan;
    }

    private void validatePlanType(AssistantPlan plan, List<String> violations) {
        if (plan.planType() == PlanType.SINGLE_TASK && plan.tasks().size() != 1) {
            violations.add("SINGLE_TASK 必须且只能包含一个任务");
        }
        if (plan.planType() == PlanType.MULTI_TASK && plan.tasks().size() < 2) {
            violations.add("MULTI_TASK 至少包含两个任务");
        }
        if (plan.planType() == PlanType.CLARIFY
                && plan.tasks().stream().noneMatch(task -> task.action() == AssistantAction.ASK_CLARIFICATION)) {
            violations.add("CLARIFY 计划必须包含 ASK_CLARIFICATION");
        }
    }

    private void validateTasks(List<AssistantTask> tasks, List<String> violations) {
        Set<String> taskIds = new HashSet<>();
        for (int index = 0; index < tasks.size(); index++) {
            AssistantTask task = tasks.get(index);
            String label = "tasks[" + index + "]";
            if (task == null) {
                violations.add(label + " 不能为空");
                continue;
            }
            if (task.taskId() == null || !TASK_ID_PATTERN.matcher(task.taskId()).matches()) {
                violations.add(label + ".taskId 格式非法");
            } else if (!taskIds.add(task.taskId())) {
                violations.add("taskId 重复: " + task.taskId());
            }
            if (task.intent() == null) {
                violations.add(label + ".intent 不能为空");
            }
            if (task.action() == null) {
                violations.add(label + ".action 不能为空");
            }
            if (task.executionMode() == null) {
                violations.add(label + ".executionMode 不能为空");
            }
            if (task.action() != null && task.executionMode() != null) {
                ExecutionMode expectedMode = EXPECTED_MODES.get(task.action());
                if (expectedMode != task.executionMode()) {
                    violations.add(label + ".executionMode 应为 " + expectedMode);
                }
            }
            if (task.action() != null && task.intent() != null
                    && !ALLOWED_INTENTS.getOrDefault(task.action(), Set.of()).contains(task.intent())) {
                violations.add(label + ".intent 与 action 不匹配");
            }
            validateSlots(task, label, violations);
            validateConditions(task, label, violations);
            if (task.confidence() != null && (task.confidence() < 0 || task.confidence() > 1)) {
                violations.add(label + ".confidence 必须在 0 到 1 之间");
            }
            if (task.reason() != null && task.reason().length() > MAX_REASON_LENGTH) {
                violations.add(label + ".reason 超过 " + MAX_REASON_LENGTH + " 字符");
            }
        }
    }

    private void validateSlots(AssistantTask task, String label, List<String> violations) {
        if (task.slots() == null) {
            violations.add(label + ".slots 不能为空");
            return;
        }
        if (task.missingSlots() == null) {
            violations.add(label + ".missingSlots 不能为空");
            return;
        }
        if (task.action() == null) {
            return;
        }
        Set<String> allowed = ALLOWED_SLOTS.getOrDefault(task.action(), Set.of());
        Set<String> required = REQUIRED_SLOTS.getOrDefault(task.action(), Set.of());
        for (Map.Entry<String, Object> slot : task.slots().entrySet()) {
            if (!allowed.contains(slot.getKey())) {
                violations.add(label + ".slots 包含未允许字段: " + slot.getKey());
            }
            Object value = slot.getValue();
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                violations.add(label + ".slots." + slot.getKey() + " 类型不受支持");
            }
            if (value instanceof String text && text.length() > MAX_SLOT_TEXT_LENGTH) {
                violations.add(label + ".slots." + slot.getKey() + " 超过长度限制");
            }
        }
        for (String missingSlot : task.missingSlots()) {
            if (!required.contains(missingSlot)) {
                violations.add(label + ".missingSlots 包含非必填字段: " + missingSlot);
            }
        }
        for (String requiredSlot : required) {
            if (!hasSlotValue(task.slots().get(requiredSlot)) && !task.missingSlots().contains(requiredSlot)) {
                violations.add(label + " 缺少必填槽位声明: " + requiredSlot);
            }
            if (hasSlotValue(task.slots().get(requiredSlot)) && task.missingSlots().contains(requiredSlot)) {
                violations.add(label + " 槽位已提供但仍标记缺失: " + requiredSlot);
            }
        }
    }

    private void validateConditions(AssistantTask task, String label, List<String> violations) {
        if (task.dependsOn() == null) {
            violations.add(label + ".dependsOn 不能为空");
        }
        if (task.conditions() == null) {
            violations.add(label + ".conditions 不能为空");
            return;
        }
        for (int index = 0; index < task.conditions().size(); index++) {
            TaskCondition condition = task.conditions().get(index);
            String conditionLabel = label + ".conditions[" + index + "]";
            if (condition == null) {
                violations.add(conditionLabel + " 不能为空");
                continue;
            }
            if (condition.sourceTaskId() == null || condition.sourceTaskId().isBlank()) {
                violations.add(conditionLabel + ".sourceTaskId 不能为空");
            } else if (task.dependsOn() != null && !task.dependsOn().contains(condition.sourceTaskId())) {
                violations.add(conditionLabel + ".sourceTaskId 必须是当前任务的直接依赖");
            }
            if (condition.field() == null || condition.operator() == null) {
                violations.add(conditionLabel + " 的 field/operator 不能为空");
                continue;
            }
            List<String> expected = condition.expectedValues();
            if (expected == null) {
                violations.add(conditionLabel + ".expectedValues 不能为空，空值判断请传 []");
                continue;
            }
            if (condition.operator() == ConditionOperator.EQ && (expected == null || expected.size() != 1)) {
                violations.add(conditionLabel + " 使用 EQ 时必须有一个 expectedValue");
            }
            if (condition.operator() == ConditionOperator.IN && (expected == null || expected.isEmpty())) {
                violations.add(conditionLabel + " 使用 IN 时 expectedValues 不能为空");
            }
            if ((condition.operator() == ConditionOperator.IS_NULL || condition.operator() == ConditionOperator.NOT_NULL)
                    && expected != null && !expected.isEmpty()) {
                violations.add(conditionLabel + " 使用空值判断时不能携带 expectedValues");
            }
            validateConditionField(condition, conditionLabel, violations);
        }
    }

    private void validateConditionField(TaskCondition condition, String label, List<String> violations) {
        if (condition.field() == ConditionField.ORDER_STATUS) {
            if (!(condition.operator() == ConditionOperator.EQ || condition.operator() == ConditionOperator.IN)) {
                violations.add(label + " 的 ORDER_STATUS 只支持 EQ/IN");
                return;
            }
            if (condition.expectedValues() != null) {
                for (String value : condition.expectedValues()) {
                    try {
                        OrderStatus.valueOf(value);
                    } catch (IllegalArgumentException ex) {
                        violations.add(label + " 包含未知订单状态: " + value);
                    }
                }
            }
        }
        if (condition.field() == ConditionField.ORDER_SHIPPED_AT
                && !(condition.operator() == ConditionOperator.IS_NULL
                || condition.operator() == ConditionOperator.NOT_NULL)) {
            violations.add(label + " 的 ORDER_SHIPPED_AT 只支持 IS_NULL/NOT_NULL");
        }
    }

    private void validateDependencies(List<AssistantTask> tasks, List<String> violations) {
        Map<String, AssistantTask> taskMap = new HashMap<>();
        for (AssistantTask task : tasks) {
            if (task != null && task.taskId() != null) {
                taskMap.putIfAbsent(task.taskId(), task);
            }
        }
        for (AssistantTask task : tasks) {
            if (task == null || task.taskId() == null || task.dependsOn() == null) {
                continue;
            }
            for (String dependency : task.dependsOn()) {
                if (!taskMap.containsKey(dependency)) {
                    violations.add(task.taskId() + " 引用了不存在的依赖: " + dependency);
                }
                if (task.taskId().equals(dependency)) {
                    violations.add(task.taskId() + " 不能依赖自身");
                }
            }
        }
        if (containsCycle(taskMap)) {
            violations.add("任务依赖存在循环");
        }
    }

    private boolean containsCycle(Map<String, AssistantTask> taskMap) {
        Map<String, Integer> states = new HashMap<>();
        for (String taskId : taskMap.keySet()) {
            if (visit(taskId, taskMap, states)) {
                return true;
            }
        }
        return false;
    }

    private boolean visit(String taskId, Map<String, AssistantTask> taskMap, Map<String, Integer> states) {
        int state = states.getOrDefault(taskId, 0);
        if (state == 1) {
            return true;
        }
        if (state == 2) {
            return false;
        }
        states.put(taskId, 1);
        AssistantTask task = taskMap.get(taskId);
        if (task != null && task.dependsOn() != null) {
            for (String dependency : task.dependsOn()) {
                if (taskMap.containsKey(dependency) && visit(dependency, taskMap, states)) {
                    return true;
                }
            }
        }
        states.put(taskId, 2);
        return false;
    }

    private boolean hasSlotValue(Object value) {
        return value != null && (!(value instanceof String text) || !text.isBlank());
    }

    private static Map<AssistantAction, ExecutionMode> expectedModes() {
        Map<AssistantAction, ExecutionMode> result = new EnumMap<>(AssistantAction.class);
        result.put(AssistantAction.QUERY_ORDER, ExecutionMode.TOOL_READ);
        result.put(AssistantAction.QUERY_LOGISTICS, ExecutionMode.TOOL_READ);
        result.put(AssistantAction.SEARCH_PRODUCT, ExecutionMode.TOOL_READ);
        result.put(AssistantAction.SEARCH_KNOWLEDGE, ExecutionMode.TOOL_READ);
        result.put(AssistantAction.CHECK_PROMOTION, ExecutionMode.TOOL_READ);
        result.put(AssistantAction.CREATE_ORDER_DRAFT, ExecutionMode.CREATE_DRAFT);
        result.put(AssistantAction.CANCEL_ORDER, ExecutionMode.ASK_CONFIRM);
        result.put(AssistantAction.PAY_ORDER, ExecutionMode.ASK_CONFIRM);
        result.put(AssistantAction.REQUEST_REFUND, ExecutionMode.ASK_CONFIRM);
        result.put(AssistantAction.CONFIRM_RECEIPT, ExecutionMode.ASK_CONFIRM);
        result.put(AssistantAction.UPDATE_ADDRESS, ExecutionMode.ASK_CONFIRM);
        result.put(AssistantAction.HANDOFF, ExecutionMode.ASK_CONFIRM);
        result.put(AssistantAction.ASK_CLARIFICATION, ExecutionMode.CLARIFY);
        result.put(AssistantAction.GENERAL_CHAT, ExecutionMode.ANSWER_ONLY);
        result.put(AssistantAction.COMPOSE_ANSWER, ExecutionMode.ANSWER_ONLY);
        return Map.copyOf(result);
    }

    private static Map<AssistantAction, Set<String>> allowedSlots() {
        Map<AssistantAction, Set<String>> result = new EnumMap<>(AssistantAction.class);
        result.put(AssistantAction.QUERY_ORDER, Set.of("orderNo"));
        result.put(AssistantAction.QUERY_LOGISTICS, Set.of("orderNo"));
        result.put(AssistantAction.SEARCH_PRODUCT, Set.of("query", "category", "budgetMin", "budgetMax"));
        result.put(AssistantAction.SEARCH_KNOWLEDGE, Set.of("query"));
        result.put(AssistantAction.CHECK_PROMOTION, Set.of("query", "productId"));
        result.put(AssistantAction.CREATE_ORDER_DRAFT, Set.of("productId", "productQuery", "quantity"));
        result.put(AssistantAction.CANCEL_ORDER, Set.of("orderNo", "note"));
        result.put(AssistantAction.PAY_ORDER, Set.of("orderNo", "paymentMethod"));
        result.put(AssistantAction.REQUEST_REFUND, Set.of("orderNo", "reason"));
        result.put(AssistantAction.CONFIRM_RECEIPT, Set.of("orderNo"));
        result.put(AssistantAction.UPDATE_ADDRESS, Set.of("orderNo", "address"));
        result.put(AssistantAction.HANDOFF, Set.of("reason"));
        result.put(AssistantAction.ASK_CLARIFICATION, Set.of("question", "missingForTaskId"));
        result.put(AssistantAction.GENERAL_CHAT, Set.of("query"));
        result.put(AssistantAction.COMPOSE_ANSWER, Set.of());
        return Map.copyOf(result);
    }

    private static Map<AssistantAction, Set<String>> requiredSlots() {
        Map<AssistantAction, Set<String>> result = new EnumMap<>(AssistantAction.class);
        result.put(AssistantAction.QUERY_ORDER, Set.of("orderNo"));
        result.put(AssistantAction.QUERY_LOGISTICS, Set.of("orderNo"));
        result.put(AssistantAction.SEARCH_PRODUCT, Set.of("query"));
        result.put(AssistantAction.SEARCH_KNOWLEDGE, Set.of("query"));
        result.put(AssistantAction.CHECK_PROMOTION, Set.of("query"));
        result.put(AssistantAction.CREATE_ORDER_DRAFT, Set.of("productQuery"));
        result.put(AssistantAction.CANCEL_ORDER, Set.of("orderNo"));
        result.put(AssistantAction.PAY_ORDER, Set.of("orderNo"));
        result.put(AssistantAction.REQUEST_REFUND, Set.of("orderNo"));
        result.put(AssistantAction.CONFIRM_RECEIPT, Set.of("orderNo"));
        result.put(AssistantAction.UPDATE_ADDRESS, Set.of("orderNo", "address"));
        result.put(AssistantAction.HANDOFF, Set.of());
        result.put(AssistantAction.ASK_CLARIFICATION, Set.of("question"));
        result.put(AssistantAction.GENERAL_CHAT, Set.of());
        result.put(AssistantAction.COMPOSE_ANSWER, Set.of());
        return Map.copyOf(result);
    }

    private static Map<AssistantAction, Set<AssistantIntent>> allowedIntents() {
        Map<AssistantAction, Set<AssistantIntent>> result = new EnumMap<>(AssistantAction.class);
        result.put(AssistantAction.QUERY_ORDER, Set.of(AssistantIntent.ORDER));
        result.put(AssistantAction.QUERY_LOGISTICS, Set.of(AssistantIntent.ORDER));
        result.put(AssistantAction.SEARCH_PRODUCT, Set.of(AssistantIntent.PRODUCT));
        result.put(AssistantAction.SEARCH_KNOWLEDGE, Set.of(
                AssistantIntent.KNOWLEDGE, AssistantIntent.ORDER, AssistantIntent.AFTER_SALES,
                AssistantIntent.PROMOTION, AssistantIntent.PROFILE));
        result.put(AssistantAction.CHECK_PROMOTION, Set.of(AssistantIntent.PROMOTION));
        result.put(AssistantAction.CREATE_ORDER_DRAFT, Set.of(AssistantIntent.ORDER));
        result.put(AssistantAction.CANCEL_ORDER, Set.of(AssistantIntent.ORDER));
        result.put(AssistantAction.PAY_ORDER, Set.of(AssistantIntent.ORDER));
        result.put(AssistantAction.REQUEST_REFUND, Set.of(AssistantIntent.AFTER_SALES));
        result.put(AssistantAction.CONFIRM_RECEIPT, Set.of(AssistantIntent.ORDER));
        result.put(AssistantAction.UPDATE_ADDRESS, Set.of(AssistantIntent.PROFILE, AssistantIntent.ORDER));
        result.put(AssistantAction.HANDOFF, Set.of(AssistantIntent.HANDOFF));
        result.put(AssistantAction.ASK_CLARIFICATION, EnumSet.allOf(AssistantIntent.class));
        result.put(AssistantAction.GENERAL_CHAT, Set.of(AssistantIntent.CHAT));
        result.put(AssistantAction.COMPOSE_ANSWER, EnumSet.allOf(AssistantIntent.class));
        return Map.copyOf(result);
    }
}

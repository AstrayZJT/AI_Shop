package com.aishop.assistant.context;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.AssistantTask;
import com.aishop.assistant.model.PlannerResult;
import com.aishop.assistant.validation.PlanValidator;

@Service
public class ConversationPlanResolver {

    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("ORD-[A-Z0-9]{8}", Pattern.CASE_INSENSITIVE);
    private static final Set<AssistantAction> ORDER_TARGET_ACTIONS = EnumSet.of(
            AssistantAction.QUERY_ORDER,
            AssistantAction.QUERY_LOGISTICS,
            AssistantAction.CANCEL_ORDER,
            AssistantAction.PAY_ORDER,
            AssistantAction.REQUEST_REFUND,
            AssistantAction.CONFIRM_RECEIPT,
            AssistantAction.UPDATE_ADDRESS);

    private final PlanValidator planValidator;

    public ConversationPlanResolver(PlanValidator planValidator) {
        this.planValidator = planValidator;
    }

    public PlannerResult resolve(AssistantContext context, PlannerResult plannerResult) {
        Set<String> explicitOrderNos = explicitOrderNos(context.currentMessage());
        List<AssistantTask> tasks = plannerResult.plan().tasks().stream()
                .map(task -> resolveTask(task, context.resolvedOrderNo(), explicitOrderNos))
                .toList();
        AssistantPlan plan = planValidator.validate(new AssistantPlan(
                plannerResult.plan().planType(), tasks, plannerResult.plan().summary()));
        return new PlannerResult(
                plan,
                plannerResult.source(),
                plannerResult.promptVersion(),
                plannerResult.fallbackReason(),
                plannerResult.rawModelOutput(),
                plannerResult.modelName(),
                plannerResult.inputTokens(),
                plannerResult.outputTokens());
    }

    private AssistantTask resolveTask(AssistantTask task,
                                      String resolvedOrderNo,
                                      Set<String> explicitOrderNos) {
        if (!ORDER_TARGET_ACTIONS.contains(task.action())) {
            return task;
        }
        LinkedHashMap<String, Object> slots = new LinkedHashMap<>(task.slots());
        List<String> missingSlots = new ArrayList<>(task.missingSlots());
        String plannedOrderNo = normalizeOrderNo(slots.get("orderNo"));

        boolean plannedOrderIsGrounded = plannedOrderNo != null
                && (explicitOrderNos.contains(plannedOrderNo)
                || plannedOrderNo.equals(resolvedOrderNo));
        if (!plannedOrderIsGrounded) {
            slots.remove("orderNo");
        }
        if (!slots.containsKey("orderNo") && resolvedOrderNo != null) {
            slots.put("orderNo", resolvedOrderNo);
        }
        if (slots.containsKey("orderNo")) {
            missingSlots.remove("orderNo");
        } else if (!missingSlots.contains("orderNo")) {
            missingSlots.add("orderNo");
        }

        return new AssistantTask(
                task.taskId(), task.intent(), task.action(), task.executionMode(), slots,
                missingSlots, task.dependsOn(), task.conditions(), task.confidence(), task.reason());
    }

    private Set<String> explicitOrderNos(String message) {
        java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
        Matcher matcher = ORDER_NO_PATTERN.matcher(message == null ? "" : message);
        while (matcher.find()) {
            values.add(matcher.group().toUpperCase(Locale.ROOT));
        }
        return Set.copyOf(values);
    }

    private String normalizeOrderNo(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        String normalized = text.strip().toUpperCase(Locale.ROOT);
        return ORDER_NO_PATTERN.matcher(normalized).matches() ? normalized : null;
    }
}

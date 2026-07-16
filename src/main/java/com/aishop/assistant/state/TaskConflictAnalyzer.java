package com.aishop.assistant.state;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.AssistantTask;

@Component
public class TaskConflictAnalyzer {

    private static final Set<AssistantAction> ORDER_WRITES = EnumSet.of(
            AssistantAction.CANCEL_ORDER,
            AssistantAction.PAY_ORDER,
            AssistantAction.REQUEST_REFUND,
            AssistantAction.CONFIRM_RECEIPT,
            AssistantAction.UPDATE_ADDRESS);

    public List<TaskConflict> analyze(AssistantPlan plan) {
        List<TaskConflict> conflicts = new ArrayList<>();
        List<AssistantTask> tasks = plan.tasks();
        for (int leftIndex = 0; leftIndex < tasks.size(); leftIndex++) {
            AssistantTask left = tasks.get(leftIndex);
            if (!ORDER_WRITES.contains(left.action())) {
                continue;
            }
            for (int rightIndex = leftIndex + 1; rightIndex < tasks.size(); rightIndex++) {
                AssistantTask right = tasks.get(rightIndex);
                if (!ORDER_WRITES.contains(right.action()) || !sameTarget(left, right)) {
                    continue;
                }
                if (isConflict(left.action(), right.action())) {
                    conflicts.add(new TaskConflict(
                            left.taskId(), left.action(), right.taskId(), right.action(),
                            target(left), "同一订单不能在一个计划中同时执行互斥写操作"));
                }
            }
        }
        return List.copyOf(conflicts);
    }

    private boolean sameTarget(AssistantTask left, AssistantTask right) {
        String leftTarget = target(left);
        String rightTarget = target(right);
        if (leftTarget == null && rightTarget == null) {
            return true;
        }
        return leftTarget != null && leftTarget.equals(rightTarget);
    }

    private boolean isConflict(AssistantAction left, AssistantAction right) {
        if (left == right) {
            return true;
        }
        return left == AssistantAction.CANCEL_ORDER || right == AssistantAction.CANCEL_ORDER;
    }

    private String target(AssistantTask task) {
        Object value = task.slots().get("orderNo");
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text.strip().toUpperCase(Locale.ROOT);
    }
}

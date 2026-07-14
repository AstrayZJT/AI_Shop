package com.aishop.assistant.validation;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.PlannerInput;

@Component
public class PlanSemanticGuard {

    private static final Set<AssistantAction> WRITE_ACTIONS = EnumSet.of(
            AssistantAction.CANCEL_ORDER,
            AssistantAction.PAY_ORDER,
            AssistantAction.REQUEST_REFUND,
            AssistantAction.CONFIRM_RECEIPT,
            AssistantAction.UPDATE_ADDRESS,
            AssistantAction.CREATE_ORDER_DRAFT);

    public AssistantPlan validate(PlannerInput input, AssistantPlan plan) {
        String text = input.message() == null ? "" : input.message().toLowerCase();
        boolean consultation = containsAny(
                text, "怎么", "如何", "是什么", "规则", "政策", "支持吗", "可以吗", "可不可以");
        boolean explicitExecution = containsAny(
                text, "帮我", "给我", "直接", "现在", "马上", "立刻", "提交", "申请一下", "替我");
        boolean containsWriteAction = plan.tasks().stream()
                .anyMatch(task -> WRITE_ACTIONS.contains(task.action()));

        if (consultation && !explicitExecution && containsWriteAction) {
            throw new PlanValidationException(List.of("纯咨询消息不能规划写操作"));
        }
        return plan;
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }
}

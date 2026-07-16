package com.aishop.assistant.guardrail;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.tool.ToolRiskLevel;

@Component
public class ActionPolicyRegistry {

    private final Map<AssistantAction, ActionPolicy> policies;

    public ActionPolicyRegistry() {
        EnumMap<AssistantAction, ActionPolicy> values = new EnumMap<>(AssistantAction.class);
        readOnly(values,
                AssistantAction.QUERY_ORDER,
                AssistantAction.QUERY_LOGISTICS,
                AssistantAction.SEARCH_PRODUCT,
                AssistantAction.SEARCH_KNOWLEDGE,
                AssistantAction.CHECK_PROMOTION,
                AssistantAction.ASK_CLARIFICATION,
                AssistantAction.GENERAL_CHAT,
                AssistantAction.COMPOSE_ANSWER);
        prepareOnly(values, true, AssistantAction.CANCEL_ORDER);
        prepareOnly(values, false,
                AssistantAction.PAY_ORDER,
                AssistantAction.REQUEST_REFUND,
                AssistantAction.CONFIRM_RECEIPT,
                AssistantAction.UPDATE_ADDRESS,
                AssistantAction.HANDOFF,
                AssistantAction.CREATE_ORDER_DRAFT);
        if (values.size() != AssistantAction.values().length) {
            throw new IllegalStateException("ActionPolicy 未覆盖所有 AssistantAction");
        }
        this.policies = Collections.unmodifiableMap(values);
    }

    public ActionPolicy require(AssistantAction action) {
        ActionPolicy policy = policies.get(action);
        if (policy == null) {
            throw new SecurityException("未注册 action 策略: " + action);
        }
        return policy;
    }

    public List<ActionPolicy> all() {
        return List.copyOf(policies.values());
    }

    public void validateToolResult(AssistantAction action,
                                   com.aishop.assistant.tool.ToolExecutionStatus status) {
        ActionPolicy policy = require(action);
        if (policy.confirmationRequired()
                && status == com.aishop.assistant.tool.ToolExecutionStatus.SUCCEEDED) {
            throw new SecurityException("高风险 action 不能在确认前执行成功: " + action);
        }
        if (!policy.confirmationRequired()
                && status == com.aishop.assistant.tool.ToolExecutionStatus.PREPARED) {
            throw new SecurityException("只读 action 不应创建待确认动作: " + action);
        }
    }

    public ActionPolicy requireConfirmationExecution(AssistantAction action) {
        ActionPolicy policy = require(action);
        if (!policy.confirmationRequired() || !policy.confirmationExecutionEnabled()) {
            throw new SecurityException("action 未开放确认执行: " + action);
        }
        return policy;
    }

    private void readOnly(EnumMap<AssistantAction, ActionPolicy> values,
                          AssistantAction... actions) {
        for (AssistantAction action : actions) {
            values.put(action, new ActionPolicy(action, ToolRiskLevel.READ_ONLY, false, false));
        }
    }

    private void prepareOnly(EnumMap<AssistantAction, ActionPolicy> values,
                             boolean executionEnabled,
                             AssistantAction... actions) {
        for (AssistantAction action : actions) {
            values.put(action, new ActionPolicy(
                    action, ToolRiskLevel.PREPARE_ONLY, true, executionEnabled));
        }
    }
}

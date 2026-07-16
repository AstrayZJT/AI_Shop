package com.aishop.assistant.guardrail;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.tool.ToolRiskLevel;

public record ActionPolicy(
        AssistantAction action,
        ToolRiskLevel riskLevel,
        boolean confirmationRequired,
        boolean confirmationExecutionEnabled) {

    public ActionPolicy {
        if (action == null || riskLevel == null) {
            throw new IllegalArgumentException("action 和 riskLevel 不能为空");
        }
        if (riskLevel == ToolRiskLevel.READ_ONLY && confirmationRequired) {
            throw new IllegalArgumentException("READ_ONLY action 不应要求二次确认");
        }
        if (confirmationExecutionEnabled && !confirmationRequired) {
            throw new IllegalArgumentException("只有需要确认的 action 才能开放确认执行");
        }
    }
}

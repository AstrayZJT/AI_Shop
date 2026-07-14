package com.aishop.assistant.tool;

import com.aishop.assistant.model.AssistantAction;

public record ToolPolicy(
        String name,
        String description,
        AssistantAction action,
        ToolRiskLevel riskLevel,
        boolean autoExecutable
) {
    public ToolPolicy {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool name 不能为空");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("tool description 不能为空");
        }
        if (action == null || riskLevel == null) {
            throw new IllegalArgumentException("tool action/riskLevel 不能为空");
        }
        if (riskLevel != ToolRiskLevel.READ_ONLY && autoExecutable) {
            throw new IllegalArgumentException("只有 READ_ONLY 工具可以自动执行");
        }
    }
}

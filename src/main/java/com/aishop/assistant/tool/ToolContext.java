package com.aishop.assistant.tool;

import com.aishop.domain.AppUser;

public record ToolContext(
        AppUser user,
        String requestId
) {
    public ToolContext {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("工具调用必须绑定已登录用户");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId 不能为空");
        }
    }
}

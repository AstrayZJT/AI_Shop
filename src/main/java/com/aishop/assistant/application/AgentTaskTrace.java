package com.aishop.assistant.application;

public record AgentTaskTrace(
        String taskId,
        String action,
        String toolName,
        String status,
        String targetRef,
        String message) {
}

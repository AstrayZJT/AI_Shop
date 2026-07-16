package com.aishop.assistant.state;

public enum AgentRunStatus {
    PENDING,
    RUNNING,
    WAITING_INPUT,
    WAITING_CONFIRMATION,
    SUCCEEDED,
    SKIPPED,
    FAILED,
    EXPIRED
}

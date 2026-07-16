package com.aishop.assistant.tool;

public enum ToolExecutionStatus {
    SUCCEEDED,
    PREPARED,
    NEEDS_INPUT,
    NOT_SUPPORTED,
    FAILED,
    SKIPPED_DEPENDENCY,
    REJECTED,
    EXPIRED
}

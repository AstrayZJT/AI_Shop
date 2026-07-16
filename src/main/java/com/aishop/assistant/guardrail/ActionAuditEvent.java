package com.aishop.assistant.guardrail;

public enum ActionAuditEvent {
    PREPARED,
    CONFIRMED,
    REJECTED,
    EXECUTED,
    FAILED,
    EXPIRED,
    IDEMPOTENT_REPLAY,
    DENIED
}

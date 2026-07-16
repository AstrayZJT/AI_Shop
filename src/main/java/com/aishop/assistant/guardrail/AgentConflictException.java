package com.aishop.assistant.guardrail;

public class AgentConflictException extends RuntimeException {
    public AgentConflictException(String message) {
        super(message);
    }
}

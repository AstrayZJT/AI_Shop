package com.aishop.assistant.guardrail;

public class AgentAccessDeniedException extends RuntimeException {
    public AgentAccessDeniedException(String message) {
        super(message);
    }
}

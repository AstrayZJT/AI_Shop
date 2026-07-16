package com.aishop.assistant.guardrail;

public class AgentResourceNotFoundException extends RuntimeException {
    public AgentResourceNotFoundException(String message) {
        super(message);
    }
}

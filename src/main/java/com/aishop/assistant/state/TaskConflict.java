package com.aishop.assistant.state;

import com.aishop.assistant.model.AssistantAction;

public record TaskConflict(
        String leftTaskId,
        AssistantAction leftAction,
        String rightTaskId,
        AssistantAction rightAction,
        String targetRef,
        String reason) {
}

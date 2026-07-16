package com.aishop.assistant.application;

import com.aishop.assistant.answer.AssistantComposedAnswer;
import com.aishop.assistant.context.AssistantContext;
import com.aishop.assistant.orchestration.ToolPlanExecutionResult;
import com.aishop.assistant.rag.RagAnswerResult;

public record AssistantAgentTurn(
        AssistantContext context,
        ToolPlanExecutionResult execution,
        RagAnswerResult ragAnswer,
        AssistantComposedAnswer composedAnswer) {
}

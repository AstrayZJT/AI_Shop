package com.aishop.assistant.rag;

public record RagAnswerModelReply(
        String content,
        String modelName,
        Integer inputTokens,
        Integer outputTokens
) {
}

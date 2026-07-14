package com.aishop.assistant.rag;

public interface RagAnswerModelGateway {
    RagAnswerModelReply answer(String systemPrompt, String userPrompt);
}

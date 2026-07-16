package com.aishop.assistant.answer;

import java.util.List;

import com.aishop.assistant.rag.RagCitation;

public record AssistantComposedAnswer(
        String answer,
        String mode,
        List<RagCitation> citations) {

    public AssistantComposedAnswer {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}

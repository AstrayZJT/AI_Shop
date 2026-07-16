package com.aishop.assistant.answer;

import org.springframework.stereotype.Component;

import dev.langchain4j.model.chat.ChatModel;

@Component
public class LangChain4jAssistantAnswerModelGateway implements AssistantAnswerModelGateway {

    private final ChatModel chatModel;

    public LangChain4jAssistantAnswerModelGateway(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String answer(String prompt) {
        return chatModel.chat(prompt);
    }
}

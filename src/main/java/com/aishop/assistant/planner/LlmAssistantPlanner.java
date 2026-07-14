package com.aishop.assistant.planner;

import org.springframework.stereotype.Component;

import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.PlannerFailureCode;
import com.aishop.assistant.model.PlannerInput;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

@Component
public class LlmAssistantPlanner {

    private static final int MAX_RAW_OUTPUT_LENGTH = 32_768;

    private final PlannerModelGateway modelGateway;
    private final PlannerPromptFactory promptFactory;
    private final ObjectReader planReader;

    public LlmAssistantPlanner(PlannerModelGateway modelGateway,
                               PlannerPromptFactory promptFactory,
                               ObjectMapper objectMapper) {
        this.modelGateway = modelGateway;
        this.promptFactory = promptFactory;
        ObjectMapper strictMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.planReader = strictMapper.readerFor(AssistantPlan.class);
    }

    public LlmPlannerOutput plan(PlannerInput input) {
        PlannerModelReply reply = modelGateway.generatePlan(
                promptFactory.systemPrompt(),
                promptFactory.userPrompt(input));
        String rawOutput = reply.text();
        if (rawOutput == null || rawOutput.isBlank()) {
            throw new PlannerException(PlannerFailureCode.EMPTY_MODEL_OUTPUT, "模型没有返回计划");
        }
        if (rawOutput.length() > MAX_RAW_OUTPUT_LENGTH) {
            throw new PlannerException(PlannerFailureCode.INVALID_MODEL_OUTPUT, "模型计划超过长度限制");
        }
        try {
            AssistantPlan plan = planReader.readValue(rawOutput);
            return new LlmPlannerOutput(
                    plan,
                    rawOutput,
                    reply.modelName(),
                    reply.inputTokens(),
                    reply.outputTokens());
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new PlannerException(
                    PlannerFailureCode.INVALID_MODEL_OUTPUT,
                    "模型返回的计划不是合法 JSON",
                    rawOutput,
                    ex);
        }
    }

    public record LlmPlannerOutput(
            AssistantPlan plan,
            String rawOutput,
            String modelName,
            Integer inputTokens,
            Integer outputTokens
    ) {
    }
}

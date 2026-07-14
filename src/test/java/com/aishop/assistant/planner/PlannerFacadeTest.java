package com.aishop.assistant.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.model.PlannerFailureCode;
import com.aishop.assistant.model.PlannerInput;
import com.aishop.assistant.model.PlannerSource;
import com.aishop.assistant.validation.PlanValidator;
import com.aishop.assistant.validation.PlanSemanticGuard;
import com.aishop.config.ShopProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

class PlannerFacadeTest {

    @Test
    void usesRulePlannerWhenAiIsDisabled() {
        PlannerFacade facade = facade(false, "key", (system, user) -> {
            throw new AssertionError("disabled mode must not call model");
        });

        var result = facade.plan(input());

        assertThat(result.source()).isEqualTo(PlannerSource.RULE_FALLBACK);
        assertThat(result.fallbackReason()).isEqualTo(PlannerFailureCode.AI_DISABLED);
        assertThat(result.plan().tasks().getFirst().action()).isEqualTo(AssistantAction.QUERY_ORDER);
    }

    @Test
    void usesRulePlannerWhenApiKeyIsMissing() {
        PlannerFacade facade = facade(true, " ", (system, user) -> {
            throw new AssertionError("missing key must not call model");
        });

        var result = facade.plan(input());

        assertThat(result.source()).isEqualTo(PlannerSource.RULE_FALLBACK);
        assertThat(result.fallbackReason()).isEqualTo(PlannerFailureCode.API_KEY_MISSING);
    }

    @Test
    void returnsValidatedLlmPlanWhenModelOutputIsValid() {
        PlannerFacade facade = facade(true, "key", (system, user) ->
                new PlannerModelReply(LlmAssistantPlannerTest.validPlanJson(), "qwen-test", 100, 30));

        var result = facade.plan(input());

        assertThat(result.source()).isEqualTo(PlannerSource.LLM);
        assertThat(result.fallbackReason()).isNull();
        assertThat(result.modelName()).isEqualTo("qwen-test");
        assertThat(result.promptVersion()).isEqualTo(PlannerPromptFactory.VERSION);
    }

    @Test
    void fallsBackWhenModelReturnsInvalidJson() {
        PlannerFacade facade = facade(true, "key", (system, user) ->
                new PlannerModelReply("not-json", "qwen-test", null, null));

        var result = facade.plan(input());

        assertThat(result.source()).isEqualTo(PlannerSource.RULE_FALLBACK);
        assertThat(result.fallbackReason()).isEqualTo(PlannerFailureCode.INVALID_MODEL_OUTPUT);
        assertThat(result.rawModelOutput()).isEqualTo("not-json");
    }

    @Test
    void fallsBackWhenModelPlanViolatesBackendPolicy() {
        String unsafePlan = LlmAssistantPlannerTest.validPlanJson()
                .replace("QUERY_ORDER", "CANCEL_ORDER");
        PlannerFacade facade = facade(true, "key", (system, user) ->
                new PlannerModelReply(unsafePlan, "qwen-test", null, null));

        var result = facade.plan(input());

        assertThat(result.source()).isEqualTo(PlannerSource.RULE_FALLBACK);
        assertThat(result.fallbackReason()).isEqualTo(PlannerFailureCode.INVALID_MODEL_PLAN);
    }

    @Test
    void fallsBackWhenModelCallFails() {
        PlannerFacade facade = facade(true, "key", (system, user) -> {
            throw new PlannerException(PlannerFailureCode.MODEL_CALL_FAILED, "timeout");
        });

        var result = facade.plan(input());

        assertThat(result.source()).isEqualTo(PlannerSource.RULE_FALLBACK);
        assertThat(result.fallbackReason()).isEqualTo(PlannerFailureCode.MODEL_CALL_FAILED);
    }

    private PlannerFacade facade(boolean enabled, String apiKey, PlannerModelGateway gateway) {
        ObjectMapper objectMapper = new ObjectMapper();
        var properties = new ShopProperties(
                new ShopProperties.Ai(
                        enabled,
                        "https://example.test/v1",
                        apiKey,
                        "qwen-test",
                        "embedding-test",
                        false,
                        false,
                        Duration.ofSeconds(5),
                        0,
                        1000,
                        0.0),
                new ShopProperties.Rag(4));
        var llmPlanner = new LlmAssistantPlanner(
                gateway,
                new PlannerPromptFactory(objectMapper),
                objectMapper);
        return new PlannerFacade(
                properties,
                llmPlanner,
                new RuleAssistantPlanner(),
                new PlanValidator(),
                new PlanSemanticGuard());
    }

    private PlannerInput input() {
        return new PlannerInput("查询订单 ORD-12345678", null, null);
    }
}

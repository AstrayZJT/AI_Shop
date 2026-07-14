package com.aishop.assistant.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.model.PlannerFailureCode;
import com.aishop.assistant.model.PlannerInput;
import com.fasterxml.jackson.databind.ObjectMapper;

class LlmAssistantPlannerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesStrictJsonPlan() {
        CapturingGateway gateway = new CapturingGateway(new PlannerModelReply(validPlanJson(), "qwen-test", 100, 50));
        LlmAssistantPlanner planner = planner(gateway);

        var output = planner.plan(new PlannerInput("查询订单 ORD-12345678", null, null));

        assertThat(output.plan().tasks().getFirst().action()).isEqualTo(AssistantAction.QUERY_ORDER);
        assertThat(output.modelName()).isEqualTo("qwen-test");
        assertThat(output.inputTokens()).isEqualTo(100);
        assertThat(gateway.systemPrompt).contains("只负责理解和规划");
        assertThat(gateway.userPrompt).contains("<untrusted_input>");
    }

    @Test
    void rejectsMalformedJsonAndKeepsRawOutputForDebugging() {
        LlmAssistantPlanner planner = planner(new CapturingGateway(
                new PlannerModelReply("{not-json}", "qwen-test", null, null)));

        assertThatThrownBy(() -> planner.plan(new PlannerInput("查询订单", null, null)))
                .isInstanceOfSatisfying(PlannerException.class, ex -> {
                    assertThat(ex.code()).isEqualTo(PlannerFailureCode.INVALID_MODEL_OUTPUT);
                    assertThat(ex.rawModelOutput()).isEqualTo("{not-json}");
                });
    }

    @Test
    void rejectsMarkdownCodeFenceInsteadOfSilentlyExtractingJson() {
        String fenced = "```json\n" + validPlanJson() + "\n```";
        LlmAssistantPlanner planner = planner(new CapturingGateway(
                new PlannerModelReply(fenced, "qwen-test", null, null)));

        assertThatThrownBy(() -> planner.plan(new PlannerInput("查询订单", null, null)))
                .isInstanceOf(PlannerException.class)
                .hasMessageContaining("不是合法 JSON");
    }

    @Test
    void rejectsEmptyModelOutput() {
        LlmAssistantPlanner planner = planner(new CapturingGateway(
                new PlannerModelReply("  ", "qwen-test", null, null)));

        assertThatThrownBy(() -> planner.plan(new PlannerInput("查询订单", null, null)))
                .isInstanceOfSatisfying(PlannerException.class,
                        ex -> assertThat(ex.code()).isEqualTo(PlannerFailureCode.EMPTY_MODEL_OUTPUT));
    }

    @Test
    void rejectsUnknownJsonFields() {
        String outputWithUnknownField = validPlanJson().replace(
                "\"summary\": \"查询订单\"",
                "\"summary\": \"查询订单\", \"riskLevel\": \"SAFE\"");
        LlmAssistantPlanner planner = planner(new CapturingGateway(
                new PlannerModelReply(outputWithUnknownField, "qwen-test", null, null)));

        assertThatThrownBy(() -> planner.plan(new PlannerInput("查询订单", null, null)))
                .isInstanceOfSatisfying(PlannerException.class,
                        ex -> assertThat(ex.code()).isEqualTo(PlannerFailureCode.INVALID_MODEL_OUTPUT));
    }

    private LlmAssistantPlanner planner(PlannerModelGateway gateway) {
        return new LlmAssistantPlanner(
                gateway,
                new PlannerPromptFactory(objectMapper),
                objectMapper);
    }

    static String validPlanJson() {
        return """
                {
                  "planType": "SINGLE_TASK",
                  "tasks": [{
                    "taskId": "t1",
                    "intent": "ORDER",
                    "action": "QUERY_ORDER",
                    "executionMode": "TOOL_READ",
                    "slots": {"orderNo": "ORD-12345678"},
                    "missingSlots": [],
                    "dependsOn": [],
                    "conditions": [],
                    "confidence": 0.92,
                    "reason": "查询订单"
                  }],
                  "summary": "查询订单"
                }
                """;
    }

    private static final class CapturingGateway implements PlannerModelGateway {
        private final PlannerModelReply reply;
        private String systemPrompt;
        private String userPrompt;

        private CapturingGateway(PlannerModelReply reply) {
            this.reply = reply;
        }

        @Override
        public PlannerModelReply generatePlan(String systemPrompt, String userPrompt) {
            this.systemPrompt = systemPrompt;
            this.userPrompt = userPrompt;
            return reply;
        }
    }
}

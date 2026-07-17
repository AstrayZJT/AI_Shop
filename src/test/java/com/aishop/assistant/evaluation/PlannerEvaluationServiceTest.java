package com.aishop.assistant.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.aishop.assistant.planner.PlannerFacade;
import com.aishop.assistant.planner.RuleAssistantPlanner;
import com.fasterxml.jackson.databind.ObjectMapper;

class PlannerEvaluationServiceTest {

    @Test
    void evaluatesDeterministicRuleBaselineAgainstAllCases() {
        PlannerEvaluationService service = new PlannerEvaluationService(
                mock(PlannerFacade.class),
                new RuleAssistantPlanner(),
                new ObjectMapper());

        PlannerEvaluationResult result = service.evaluate(PlannerEvaluationMode.RULE_BASELINE);
        assertThat(result.totalCases())
                .isEqualTo(57);
        assertThat(result.passedCases()).isEqualTo(57);
        assertThat(result.failedCases()).isZero();
        assertThat(result.intentAccuracy()).isEqualTo(1.0);
        assertThat(result.actionExactMatch()).isEqualTo(1.0);
        assertThat(result.slotExactMatch()).isEqualTo(1.0);
        assertThat(result.taskCountAccuracy()).isEqualTo(1.0);
        assertThat(result.multiTaskAccuracy()).isEqualTo(1.0);
        assertThat(result.dependencyExactMatch()).isEqualTo(1.0);
        assertThat(result.ruleFallbackRate()).isEqualTo(1.0);
    }
}

package com.aishop.assistant.evaluation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.aishop.assistant.model.AssistantTask;
import com.aishop.assistant.model.PlannerInput;
import com.aishop.assistant.model.PlannerResult;
import com.aishop.assistant.model.PlannerSource;
import com.aishop.assistant.planner.PlannerFacade;
import com.aishop.assistant.planner.RuleAssistantPlanner;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PlannerEvaluationService {

    static final String DATASET = "assistant/evaluation/planner-cases.jsonl";

    private final PlannerFacade plannerFacade;
    private final RuleAssistantPlanner rulePlanner;
    private final ObjectMapper objectMapper;

    public PlannerEvaluationService(PlannerFacade plannerFacade,
                                    RuleAssistantPlanner rulePlanner,
                                    ObjectMapper objectMapper) {
        this.plannerFacade = plannerFacade;
        this.rulePlanner = rulePlanner;
        this.objectMapper = objectMapper;
    }

    public PlannerEvaluationResult evaluate(PlannerEvaluationMode mode) {
        List<PlannerEvaluationCase> cases = loadCases();
        List<PlannerEvaluationResult.PlannerCaseResult> results = new ArrayList<>();
        int intentMatches = 0;
        int actionMatches = 0;
        int slotMatches = 0;
        int taskCountMatches = 0;
        int multiTaskMatches = 0;
        int dependencyMatches = 0;
        int fallbackCount = 0;
        for (PlannerEvaluationCase evaluationCase : cases) {
            PlannerCaseEvaluation evaluation = evaluateCase(evaluationCase, mode);
            results.add(evaluation.result());
            intentMatches += evaluation.intentMatched() ? 1 : 0;
            actionMatches += evaluation.actionMatched() ? 1 : 0;
            slotMatches += evaluation.slotsMatched() ? 1 : 0;
            taskCountMatches += evaluation.taskCountMatched() ? 1 : 0;
            multiTaskMatches += evaluation.multiTaskMatched() ? 1 : 0;
            dependencyMatches += evaluation.dependenciesMatched() ? 1 : 0;
            fallbackCount += "RULE_FALLBACK".equals(evaluation.result().plannerSource()) ? 1 : 0;
        }
        int total = cases.size();
        return new PlannerEvaluationResult(
                mode.name(),
                DATASET,
                total,
                (int) results.stream().filter(PlannerEvaluationResult.PlannerCaseResult::passed).count(),
                (int) results.stream().filter(result -> !result.passed()).count(),
                ratio(intentMatches, total),
                ratio(actionMatches, total),
                ratio(slotMatches, total),
                ratio(taskCountMatches, total),
                ratio(multiTaskMatches, total),
                ratio(dependencyMatches, total),
                ratio(fallbackCount, total),
                results);
    }

    private PlannerCaseEvaluation evaluateCase(PlannerEvaluationCase evaluationCase,
                                               PlannerEvaluationMode mode) {
        try {
            PlannerResult planned = mode == PlannerEvaluationMode.RULE_BASELINE
                    ? ruleFallback(evaluationCase)
                    : plannerFacade.plan(new PlannerInput(
                            evaluationCase.message(),
                            evaluationCase.conversationSummary(),
                            evaluationCase.recentMessages()));
            var plan = planned.plan();
            List<String> actualIntents = plan.tasks().stream()
                    .map(task -> task.intent().name())
                    .toList();
            List<String> actualActions = plan.tasks().stream()
                    .map(task -> task.action().name())
                    .toList();
            boolean intentMatched = actualIntents.equals(evaluationCase.expectedIntents());
            boolean actionMatched = actualActions.equals(evaluationCase.expectedActions());
            boolean slotsMatched = slotsMatch(evaluationCase, plan.tasks());
            boolean taskCountMatched = plan.tasks().size() == evaluationCase.expectedTaskCount();
            boolean multiTaskMatched = (plan.tasks().size() > 1)
                    == (evaluationCase.expectedTaskCount() > 1);
            boolean dependenciesMatched = dependenciesMatch(evaluationCase, plan.tasks());
            boolean passed = intentMatched
                    && actionMatched
                    && slotsMatched
                    && taskCountMatched
                    && multiTaskMatched
                    && dependenciesMatched
                    && evaluationCase.expectedPlanType().equals(plan.planType().name());
            return new PlannerCaseEvaluation(new PlannerEvaluationResult.PlannerCaseResult(
                    evaluationCase.id(),
                    passed,
                    intentMatched,
                    actionMatched,
                    slotsMatched,
                    taskCountMatched,
                    multiTaskMatched,
                    dependenciesMatched,
                    evaluationCase.expectedPlanType(),
                    plan.planType().name(),
                    evaluationCase.expectedActions(),
                    actualActions,
                    planned.source().name(),
                    planned.fallbackReason() == null ? null : planned.fallbackReason().name(),
                    null), intentMatched, actionMatched, slotsMatched, taskCountMatched,
                    multiTaskMatched, dependenciesMatched);
        } catch (RuntimeException ex) {
            return new PlannerCaseEvaluation(new PlannerEvaluationResult.PlannerCaseResult(
                    evaluationCase.id(),
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    evaluationCase.expectedPlanType(),
                    null,
                    evaluationCase.expectedActions(),
                    List.of(),
                    null,
                    null,
                    ex.getClass().getSimpleName() + ": " + safeMessage(ex)),
                    false, false, false, false, false, false);
        }
    }

    private PlannerResult ruleFallback(PlannerEvaluationCase evaluationCase) {
        return new PlannerResult(
                rulePlanner.plan(new PlannerInput(
                        evaluationCase.message(),
                        evaluationCase.conversationSummary(),
                        evaluationCase.recentMessages())),
                PlannerSource.RULE_FALLBACK,
                "evaluation-rule-baseline",
                null,
                null,
                null,
                null,
                null);
    }

    private boolean slotsMatch(PlannerEvaluationCase evaluationCase, List<AssistantTask> tasks) {
        for (Map.Entry<String, Map<String, Object>> expected : evaluationCase.expectedSlotsByAction().entrySet()) {
            AssistantTask task = tasks.stream()
                    .filter(value -> value.action().name().equals(expected.getKey()))
                    .findFirst()
                    .orElse(null);
            if (task == null) {
                return false;
            }
            for (Map.Entry<String, Object> slot : expected.getValue().entrySet()) {
                if (!Objects.equals(String.valueOf(slot.getValue()), String.valueOf(task.slots().get(slot.getKey())))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean dependenciesMatch(PlannerEvaluationCase evaluationCase, List<AssistantTask> tasks) {
        for (Map.Entry<String, List<String>> expected : evaluationCase.expectedDependsOnByAction().entrySet()) {
            AssistantTask task = tasks.stream()
                    .filter(value -> value.action().name().equals(expected.getKey()))
                    .findFirst()
                    .orElse(null);
            if (task == null || !task.dependsOn().equals(expected.getValue())) {
                return false;
            }
        }
        return true;
    }

    private List<PlannerEvaluationCase> loadCases() {
        ClassPathResource resource = new ClassPathResource(DATASET);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8))) {
            List<PlannerEvaluationCase> cases = new ArrayList<>();
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    cases.add(objectMapper.readValue(line, PlannerEvaluationCase.class));
                } catch (Exception ex) {
                    throw new IllegalStateException("Planner 评测集第 " + lineNumber + " 行解析失败", ex);
                }
            }
            return List.copyOf(cases);
        } catch (IOException ex) {
            throw new IllegalStateException("Planner 评测集读取失败: " + DATASET, ex);
        }
    }

    private double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    private String safeMessage(RuntimeException ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private record PlannerCaseEvaluation(
            PlannerEvaluationResult.PlannerCaseResult result,
            boolean intentMatched,
            boolean actionMatched,
            boolean slotsMatched,
            boolean taskCountMatched,
            boolean multiTaskMatched,
            boolean dependenciesMatched) {
    }
}

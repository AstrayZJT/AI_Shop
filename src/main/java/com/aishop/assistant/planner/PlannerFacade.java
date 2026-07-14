package com.aishop.assistant.planner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.PlannerFailureCode;
import com.aishop.assistant.model.PlannerInput;
import com.aishop.assistant.model.PlannerResult;
import com.aishop.assistant.model.PlannerSource;
import com.aishop.assistant.validation.PlanValidationException;
import com.aishop.assistant.validation.PlanSemanticGuard;
import com.aishop.assistant.validation.PlanValidator;
import com.aishop.config.ShopProperties;

@Service
public class PlannerFacade {

    private static final Logger log = LoggerFactory.getLogger(PlannerFacade.class);
    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final int MAX_SUMMARY_LENGTH = 2000;
    private static final int MAX_RECENT_MESSAGES = 6;

    private final ShopProperties properties;
    private final LlmAssistantPlanner llmPlanner;
    private final RuleAssistantPlanner rulePlanner;
    private final PlanValidator planValidator;
    private final PlanSemanticGuard semanticGuard;

    public PlannerFacade(ShopProperties properties,
                         LlmAssistantPlanner llmPlanner,
                         RuleAssistantPlanner rulePlanner,
                         PlanValidator planValidator,
                         PlanSemanticGuard semanticGuard) {
        this.properties = properties;
        this.llmPlanner = llmPlanner;
        this.rulePlanner = rulePlanner;
        this.planValidator = planValidator;
        this.semanticGuard = semanticGuard;
    }

    public PlannerResult plan(PlannerInput input) {
        validateInput(input);
        if (!properties.ai().enabled()) {
            return fallback(input, PlannerFailureCode.AI_DISABLED, null);
        }
        if (!hasText(properties.ai().apiKey())) {
            return fallback(input, PlannerFailureCode.API_KEY_MISSING, null);
        }

        try {
            LlmAssistantPlanner.LlmPlannerOutput output = llmPlanner.plan(input);
            try {
                AssistantPlan validated = planValidator.validate(output.plan());
                semanticGuard.validate(input, validated);
                return new PlannerResult(
                        validated,
                        PlannerSource.LLM,
                        PlannerPromptFactory.VERSION,
                        null,
                        output.rawOutput(),
                        output.modelName(),
                        output.inputTokens(),
                        output.outputTokens());
            } catch (PlanValidationException ex) {
                log.warn("LLM planner validation failed: violationCount={}", ex.violations().size());
                return fallback(input, PlannerFailureCode.INVALID_MODEL_PLAN, output.rawOutput());
            }
        } catch (PlannerException ex) {
            log.warn("LLM planner failed: code={}, cause={}", ex.code(), rootCauseName(ex));
            return fallback(input, ex.code(), ex.rawModelOutput());
        }
    }

    private PlannerResult fallback(PlannerInput input,
                                   PlannerFailureCode reason,
                                   String rawModelOutput) {
        AssistantPlan plan;
        try {
            plan = planValidator.validate(rulePlanner.plan(input));
            semanticGuard.validate(input, plan);
        } catch (PlanValidationException ex) {
            throw new IllegalStateException("本地规则 Planner 生成了非法计划: " + ex.getMessage(), ex);
        }
        log.info("assistant planner fallback: reason={}, taskCount={}", reason, plan.tasks().size());
        return new PlannerResult(
                plan,
                PlannerSource.RULE_FALLBACK,
                PlannerPromptFactory.VERSION,
                reason,
                rawModelOutput,
                null,
                null,
                null);
    }

    private void validateInput(PlannerInput input) {
        if (input == null || !hasText(input.message())) {
            throw new IllegalArgumentException("message 不能为空");
        }
        if (input.message().length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message 不能超过 " + MAX_MESSAGE_LENGTH + " 字符");
        }
        if (input.conversationSummary() != null && input.conversationSummary().length() > MAX_SUMMARY_LENGTH) {
            throw new IllegalArgumentException("conversationSummary 不能超过 " + MAX_SUMMARY_LENGTH + " 字符");
        }
        if (input.recentMessages().size() > MAX_RECENT_MESSAGES) {
            throw new IllegalArgumentException("recentMessages 不能超过 " + MAX_RECENT_MESSAGES + " 条");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String rootCauseName(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }
}

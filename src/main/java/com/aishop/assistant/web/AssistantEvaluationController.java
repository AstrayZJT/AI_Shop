package com.aishop.assistant.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.assistant.evaluation.PlannerEvaluationMode;
import com.aishop.assistant.evaluation.PlannerEvaluationResult;
import com.aishop.assistant.evaluation.PlannerEvaluationService;
import com.aishop.service.AuthService;

import jakarta.servlet.http.HttpSession;

@RestController
public class AssistantEvaluationController {

    private final AuthService authService;
    private final PlannerEvaluationService evaluationService;

    public AssistantEvaluationController(AuthService authService,
                                         PlannerEvaluationService evaluationService) {
        this.authService = authService;
        this.evaluationService = evaluationService;
    }

    @GetMapping("/api/assistant/evaluation/planner")
    public PlannerEvaluationResult planner(HttpSession session,
                                           @RequestParam(defaultValue = "RULE_BASELINE")
                                           PlannerEvaluationMode mode) {
        authService.requireUser(session);
        return evaluationService.evaluate(mode);
    }
}

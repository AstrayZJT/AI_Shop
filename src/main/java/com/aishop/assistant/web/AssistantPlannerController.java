package com.aishop.assistant.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.assistant.model.PlannerInput;
import com.aishop.assistant.model.PlannerResult;
import com.aishop.assistant.planner.PlannerFacade;
import com.aishop.service.AuthService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
public class AssistantPlannerController {

    private final AuthService authService;
    private final PlannerFacade plannerFacade;

    public AssistantPlannerController(AuthService authService, PlannerFacade plannerFacade) {
        this.authService = authService;
        this.plannerFacade = plannerFacade;
    }

    @PostMapping("/api/assistant/planner/preview")
    public PlannerResult preview(HttpSession session, @Valid @RequestBody PlannerPreviewRequest request) {
        authService.requireUser(session);
        return plannerFacade.plan(new PlannerInput(
                request.message(),
                request.conversationSummary(),
                request.recentMessages()));
    }
}

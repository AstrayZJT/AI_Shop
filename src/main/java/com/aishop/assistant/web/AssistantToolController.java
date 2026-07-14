package com.aishop.assistant.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.assistant.function.FunctionCallingPreviewResult;
import com.aishop.assistant.function.NativeFunctionCallingService;
import com.aishop.assistant.model.PlannerInput;
import com.aishop.assistant.orchestration.AssistantToolOrchestrator;
import com.aishop.assistant.orchestration.ToolPlanExecutionResult;
import com.aishop.assistant.planner.PlannerFacade;
import com.aishop.assistant.tool.AssistantToolRegistry;
import com.aishop.assistant.tool.ToolPolicy;
import com.aishop.service.AuthService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
public class AssistantToolController {

    private final AuthService authService;
    private final PlannerFacade plannerFacade;
    private final AssistantToolRegistry toolRegistry;
    private final AssistantToolOrchestrator toolOrchestrator;
    private final NativeFunctionCallingService functionCallingService;

    public AssistantToolController(AuthService authService,
                                   PlannerFacade plannerFacade,
                                   AssistantToolRegistry toolRegistry,
                                   AssistantToolOrchestrator toolOrchestrator,
                                   NativeFunctionCallingService functionCallingService) {
        this.authService = authService;
        this.plannerFacade = plannerFacade;
        this.toolRegistry = toolRegistry;
        this.toolOrchestrator = toolOrchestrator;
        this.functionCallingService = functionCallingService;
    }

    @GetMapping("/api/assistant/tools")
    public List<ToolPolicy> tools(HttpSession session) {
        authService.requireUser(session);
        return toolRegistry.policies();
    }

    @PostMapping("/api/assistant/tools/plan-preview")
    public ToolPlanExecutionResult executePlan(HttpSession session,
                                               @Valid @RequestBody PlannerPreviewRequest request) {
        var user = authService.requireUser(session);
        var plannerResult = plannerFacade.plan(new PlannerInput(
                request.message(),
                request.conversationSummary(),
                request.recentMessages()));
        return toolOrchestrator.executePlan(user, plannerResult);
    }

    @PostMapping("/api/assistant/tools/function-call-preview")
    public FunctionCallingPreviewResult functionCall(HttpSession session,
                                                     @Valid @RequestBody FunctionCallingPreviewRequest request) {
        var user = authService.requireUser(session);
        return functionCallingService.preview(user, request.message());
    }
}

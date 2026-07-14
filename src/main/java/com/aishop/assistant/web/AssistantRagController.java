package com.aishop.assistant.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.assistant.rag.RagAnswerComposer;
import com.aishop.assistant.rag.RagAnswerResult;
import com.aishop.assistant.rag.RagEvaluationResult;
import com.aishop.assistant.rag.RagEvaluationService;
import com.aishop.service.AuthService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
public class AssistantRagController {

    private final AuthService authService;
    private final RagAnswerComposer answerComposer;
    private final RagEvaluationService evaluationService;

    public AssistantRagController(AuthService authService,
                                  RagAnswerComposer answerComposer,
                                  RagEvaluationService evaluationService) {
        this.authService = authService;
        this.answerComposer = answerComposer;
        this.evaluationService = evaluationService;
    }

    @PostMapping("/api/assistant/rag/preview")
    public RagAnswerResult preview(HttpSession session,
                                   @Valid @RequestBody RagPreviewRequest request) {
        authService.requireUser(session);
        return answerComposer.compose(request.question());
    }

    @GetMapping("/api/assistant/rag/evaluation")
    public RagEvaluationResult evaluate(HttpSession session) {
        authService.requireUser(session);
        return evaluationService.evaluate();
    }
}

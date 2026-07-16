package com.aishop.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.dto.AssistantDtos.ChatRequest;
import com.aishop.dto.AssistantDtos.ChatResponse;
import com.aishop.dto.AssistantDtos.ConfirmPendingActionRequest;
import com.aishop.dto.AssistantDtos.CreateSessionResponse;
import com.aishop.dto.AssistantDtos.EscalateSessionRequest;
import com.aishop.dto.AssistantDtos.MessageResponse;
import com.aishop.dto.AssistantDtos.PendingActionOperationResponse;
import com.aishop.dto.AssistantDtos.PendingActionResponse;
import com.aishop.dto.AssistantDtos.RuntimeHealthResponse;
import com.aishop.dto.AssistantDtos.SessionResponse;
import com.aishop.domain.AssistantSession;
import com.aishop.service.AssistantRuntimeStatusService;
import com.aishop.service.AssistantPendingActionService;
import com.aishop.service.AssistantService;
import com.aishop.service.AuthService;
import com.aishop.assistant.state.AssistantStateMachine;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
public class AssistantController {

    private final AuthService authService;
    private final AssistantService assistantService;
    private final AssistantRuntimeStatusService assistantRuntimeStatusService;
    private final AssistantPendingActionService pendingActionService;
    private final AssistantStateMachine stateMachine;

    public AssistantController(AuthService authService,
                               AssistantService assistantService,
                               AssistantRuntimeStatusService assistantRuntimeStatusService,
                               AssistantPendingActionService pendingActionService,
                               AssistantStateMachine stateMachine) {
        this.authService = authService;
        this.assistantService = assistantService;
        this.assistantRuntimeStatusService = assistantRuntimeStatusService;
        this.pendingActionService = pendingActionService;
        this.stateMachine = stateMachine;
    }

    @GetMapping("/api/assistant/health")
    public RuntimeHealthResponse health() {
        return assistantRuntimeStatusService.runtimeHealth();
    }

    @PostMapping("/api/assistant/chat")
    public ChatResponse chat(HttpSession session, @RequestBody ChatRequest request) {
        var user = authService.requireUser(session);
        return assistantService.chat(user, request.sessionId(), request.message(), request.threadId());
    }

    @GetMapping("/api/assistant/sessions")
    public List<SessionResponse> sessions(HttpSession session) {
        var user = authService.requireUser(session);
        return assistantService.listSessions(user).stream()
                .map(this::toSessionResponse)
                .toList();
    }

    @PostMapping("/api/assistant/sessions")
    public CreateSessionResponse createSession(HttpSession session) {
        var user = authService.requireUser(session);
        var created = assistantService.createSession(user);
        return new CreateSessionResponse(
                created.getId(),
                created.getTitle(),
                created.getSummary(),
                created.getLastIntent(),
                serviceStatus(created),
                unreadSupportCount(created),
                supportAgentDisplayName(created));
    }

    @GetMapping("/api/assistant/sessions/{id}")
    public SessionResponse session(@PathVariable Long id, HttpSession session) {
        var user = authService.requireUser(session);
        var s = assistantService.getOrCreateSession(user, id);
        return toSessionResponse(s);
    }

    @PostMapping("/api/assistant/sessions/{id}/escalate")
    public SessionResponse escalate(@PathVariable Long id,
                                    HttpSession session,
                                    @RequestBody(required = false) EscalateSessionRequest request) {
        var user = authService.requireUser(session);
        var updated = assistantService.escalateSession(user, id, request == null ? null : request.note());
        return toSessionResponse(updated);
    }

    @GetMapping("/api/assistant/sessions/{id}/messages")
    public List<MessageResponse> messages(@PathVariable Long id, HttpSession session) {
        var user = authService.requireUser(session);
        return assistantService.messages(id, user).stream()
                .map(m -> new MessageResponse(m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();
    }

    @GetMapping("/api/assistant/pending-actions")
    public List<PendingActionResponse> pendingActions(HttpSession session) {
        return pendingActionService.list(authService.requireUser(session));
    }

    @GetMapping("/api/assistant/sessions/{sessionId}/pending-actions/{pendingActionId}")
    public PendingActionResponse pendingAction(@PathVariable Long sessionId,
                                               @PathVariable Long pendingActionId,
                                               HttpSession session) {
        return pendingActionService.get(
                authService.requireUser(session), sessionId, pendingActionId);
    }

    @PostMapping("/api/assistant/sessions/{sessionId}/pending-actions/{pendingActionId}/confirm")
    public PendingActionOperationResponse confirmPendingAction(
            @PathVariable Long sessionId,
            @PathVariable Long pendingActionId,
            @Valid @RequestBody ConfirmPendingActionRequest request,
            HttpSession session) {
        var user = authService.requireUser(session);
        var execution = stateMachine.confirmPendingAction(
                user, sessionId, pendingActionId, request.clientRequestId());
        return new PendingActionOperationResponse(
                pendingActionService.get(user, sessionId, pendingActionId),
                execution.planRunId(), execution.status().name(), execution.idempotentReplay());
    }

    @PostMapping("/api/assistant/sessions/{sessionId}/pending-actions/{pendingActionId}/reject")
    public PendingActionOperationResponse rejectPendingAction(
            @PathVariable Long sessionId,
            @PathVariable Long pendingActionId,
            HttpSession session) {
        var user = authService.requireUser(session);
        var execution = stateMachine.rejectPendingAction(user, sessionId, pendingActionId);
        return new PendingActionOperationResponse(
                pendingActionService.get(user, sessionId, pendingActionId),
                execution.planRunId(), execution.status().name(), execution.idempotentReplay());
    }

    private SessionResponse toSessionResponse(AssistantSession session) {
        return new SessionResponse(
                session.getId(),
                session.getTitle(),
                session.getSummary(),
                session.getLastIntent(),
                serviceStatus(session),
                unreadSupportCount(session),
                supportAgentDisplayName(session));
    }

    private String serviceStatus(AssistantSession session) {
        String status = session.getServiceStatus();
        return status == null || status.isBlank() ? "ACTIVE" : status;
    }

    private long unreadSupportCount(AssistantSession session) {
        return session.getCustomerUnreadCount() == null ? 0L : session.getCustomerUnreadCount();
    }

    private String supportAgentDisplayName(AssistantSession session) {
        return session.getAssignedAdmin() == null ? null : session.getAssignedAdmin().getDisplayName();
    }
}

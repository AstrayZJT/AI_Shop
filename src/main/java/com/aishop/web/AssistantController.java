package com.aishop.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.dto.AssistantDtos.ChatRequest;
import com.aishop.dto.AssistantDtos.ChatResponse;
import com.aishop.dto.AssistantDtos.CreateSessionResponse;
import com.aishop.dto.AssistantDtos.EscalateSessionRequest;
import com.aishop.dto.AssistantDtos.MessageResponse;
import com.aishop.dto.AssistantDtos.RuntimeHealthResponse;
import com.aishop.dto.AssistantDtos.SessionResponse;
import com.aishop.domain.AssistantSession;
import com.aishop.service.AssistantRuntimeStatusService;
import com.aishop.service.AssistantService;
import com.aishop.service.AuthService;

import jakarta.servlet.http.HttpSession;

@RestController
public class AssistantController {

    private final AuthService authService;
    private final AssistantService assistantService;
    private final AssistantRuntimeStatusService assistantRuntimeStatusService;

    public AssistantController(AuthService authService,
                               AssistantService assistantService,
                               AssistantRuntimeStatusService assistantRuntimeStatusService) {
        this.authService = authService;
        this.assistantService = assistantService;
        this.assistantRuntimeStatusService = assistantRuntimeStatusService;
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

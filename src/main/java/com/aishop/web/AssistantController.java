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
import com.aishop.dto.AssistantDtos.MessageResponse;
import com.aishop.dto.AssistantDtos.SessionResponse;
import com.aishop.service.AssistantService;
import com.aishop.service.AuthService;

import jakarta.servlet.http.HttpSession;

@RestController
public class AssistantController {

    private final AuthService authService;
    private final AssistantService assistantService;

    public AssistantController(AuthService authService, AssistantService assistantService) {
        this.authService = authService;
        this.assistantService = assistantService;
    }

    @PostMapping("/api/assistant/chat")
    public ChatResponse chat(HttpSession session, @RequestBody ChatRequest request) {
        var user = authService.currentUser(session);
        if (user == null) {
            throw new IllegalArgumentException("请先登录");
        }
        return assistantService.chat(user, request.sessionId(), request.message(), request.threadId());
    }

    @GetMapping("/api/assistant/sessions")
    public List<SessionResponse> sessions(HttpSession session) {
        var user = authService.currentUser(session);
        if (user == null) {
            throw new IllegalArgumentException("请先登录");
        }
        return assistantService.listSessions(user).stream()
                .map(s -> new SessionResponse(s.getId(), s.getTitle(), s.getSummary(), s.getLastIntent()))
                .toList();
    }

    @PostMapping("/api/assistant/sessions")
    public CreateSessionResponse createSession(HttpSession session) {
        var user = authService.currentUser(session);
        if (user == null) {
            throw new IllegalArgumentException("请先登录");
        }
        var created = assistantService.createSession(user);
        return new CreateSessionResponse(created.getId(), created.getTitle(), created.getSummary(), created.getLastIntent());
    }

    @GetMapping("/api/assistant/sessions/{id}")
    public SessionResponse session(@PathVariable Long id, HttpSession session) {
        var user = authService.currentUser(session);
        if (user == null) {
            throw new IllegalArgumentException("请先登录");
        }
        var s = assistantService.getOrCreateSession(user, id);
        return new SessionResponse(s.getId(), s.getTitle(), s.getSummary(), s.getLastIntent());
    }

    @GetMapping("/api/assistant/sessions/{id}/messages")
    public List<MessageResponse> messages(@PathVariable Long id, HttpSession session) {
        var user = authService.currentUser(session);
        if (user == null) {
            throw new IllegalArgumentException("请先登录");
        }
        return assistantService.messages(id, user).stream()
                .map(m -> new MessageResponse(m.getRole(), m.getContent()))
                .toList();
    }
}

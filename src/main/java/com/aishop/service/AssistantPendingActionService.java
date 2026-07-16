package com.aishop.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.assistant.guardrail.AgentResourceNotFoundException;
import com.aishop.assistant.state.AgentStateCodec;
import com.aishop.domain.AppUser;
import com.aishop.domain.PendingAssistantAction;
import com.aishop.dto.AssistantDtos.PendingActionResponse;
import com.aishop.repository.PendingAssistantActionRepository;

@Service
public class AssistantPendingActionService {

    private final PendingAssistantActionRepository repository;
    private final AgentStateCodec codec;

    public AssistantPendingActionService(PendingAssistantActionRepository repository,
                                         AgentStateCodec codec) {
        this.repository = repository;
        this.codec = codec;
    }

    @Transactional(readOnly = true)
    public List<PendingActionResponse> list(AppUser user) {
        return repository.findTop20ByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PendingActionResponse get(AppUser user, Long sessionId, Long pendingActionId) {
        PendingAssistantAction pending = repository.findByIdAndUser(pendingActionId, user)
                .filter(value -> value.getSession().getId().equals(sessionId))
                .orElseThrow(() -> new AgentResourceNotFoundException("待确认动作不存在"));
        return toResponse(pending);
    }

    private PendingActionResponse toResponse(PendingAssistantAction pending) {
        return new PendingActionResponse(
                pending.getId(),
                pending.getSession().getId(),
                pending.getPlanRun().getId(),
                pending.getTaskRun().getTaskId(),
                pending.getAction().name(),
                pending.getStatus().name(),
                pending.getTargetRef(),
                codec.readMap(pending.getPreviewJson()),
                pending.getExpiresAt(),
                pending.getConfirmedAt(),
                pending.getExecutedAt(),
                pending.getRejectedAt(),
                pending.getClientRequestId(),
                pending.getResultMessage(),
                pending.getCreatedAt());
    }
}

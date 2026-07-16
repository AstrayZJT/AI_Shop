package com.aishop.assistant.guardrail;

import org.springframework.stereotype.Service;

import com.aishop.domain.AppUser;
import com.aishop.domain.AssistantActionAudit;
import com.aishop.domain.PendingAssistantAction;
import com.aishop.repository.AssistantActionAuditRepository;

@Service
public class ActionAuditService {

    private final AssistantActionAuditRepository repository;

    public ActionAuditService(AssistantActionAuditRepository repository) {
        this.repository = repository;
    }

    public AssistantActionAudit record(PendingAssistantAction pending,
                                       ActionAuditEvent event,
                                       AppUser actor,
                                       String clientRequestId,
                                       String outcome,
                                       String detail) {
        AssistantActionAudit audit = new AssistantActionAudit();
        audit.setPendingAction(pending);
        audit.setPlanRun(pending.getPlanRun());
        audit.setTaskRun(pending.getTaskRun());
        audit.setOwnerUser(pending.getUser());
        audit.setActorUser(actor);
        audit.setEvent(event);
        audit.setAction(pending.getAction());
        audit.setPlannerSource(pending.getPlanRun().getPlannerSource());
        audit.setToolName(pending.getTaskRun().getToolName());
        audit.setTargetRef(pending.getTargetRef());
        audit.setClientRequestId(clip(clientRequestId, 64));
        audit.setOutcome(clip(defaultText(outcome, event.name()), 32));
        audit.setDetail(clip(detail, 1000));
        return repository.save(audit);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String clip(String value, int maximum) {
        if (value == null || value.length() <= maximum) {
            return value;
        }
        return value.substring(0, maximum);
    }
}

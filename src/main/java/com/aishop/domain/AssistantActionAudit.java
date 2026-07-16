package com.aishop.domain;

import com.aishop.assistant.guardrail.ActionAuditEvent;
import com.aishop.assistant.model.AssistantAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "assistant_action_audits",
        indexes = {
                @Index(name = "idx_action_audit_pending", columnList = "pending_action_id,created_at"),
                @Index(name = "idx_action_audit_actor", columnList = "actor_user_id,created_at")
        })
public class AssistantActionAudit extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pending_action_id", nullable = false)
    private PendingAssistantAction pendingAction;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_run_id", nullable = false)
    private AssistantPlanRun planRun;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "task_run_id", nullable = false)
    private AssistantTaskRun taskRun;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private AppUser ownerUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private AppUser actorUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ActionAuditEvent event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AssistantAction action;

    @Column(nullable = false, length = 32)
    private String plannerSource;

    @Column(length = 128)
    private String toolName;

    @Column(nullable = false, length = 128)
    private String targetRef;

    @Column(length = 64)
    private String clientRequestId;

    @Column(nullable = false, length = 32)
    private String outcome;

    @Column(length = 1000)
    private String detail;

    public PendingAssistantAction getPendingAction() {
        return pendingAction;
    }

    public void setPendingAction(PendingAssistantAction pendingAction) {
        this.pendingAction = pendingAction;
    }

    public AssistantPlanRun getPlanRun() {
        return planRun;
    }

    public void setPlanRun(AssistantPlanRun planRun) {
        this.planRun = planRun;
    }

    public AssistantTaskRun getTaskRun() {
        return taskRun;
    }

    public void setTaskRun(AssistantTaskRun taskRun) {
        this.taskRun = taskRun;
    }

    public AppUser getOwnerUser() {
        return ownerUser;
    }

    public void setOwnerUser(AppUser ownerUser) {
        this.ownerUser = ownerUser;
    }

    public AppUser getActorUser() {
        return actorUser;
    }

    public void setActorUser(AppUser actorUser) {
        this.actorUser = actorUser;
    }

    public ActionAuditEvent getEvent() {
        return event;
    }

    public void setEvent(ActionAuditEvent event) {
        this.event = event;
    }

    public AssistantAction getAction() {
        return action;
    }

    public void setAction(AssistantAction action) {
        this.action = action;
    }

    public String getPlannerSource() {
        return plannerSource;
    }

    public void setPlannerSource(String plannerSource) {
        this.plannerSource = plannerSource;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getTargetRef() {
        return targetRef;
    }

    public void setTargetRef(String targetRef) {
        this.targetRef = targetRef;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}

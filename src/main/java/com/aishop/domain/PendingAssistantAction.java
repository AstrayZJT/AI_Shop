package com.aishop.domain;

import java.time.Instant;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.state.PendingActionStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "pending_assistant_actions",
        indexes = @Index(
                name = "idx_pending_assistant_action_expiry",
                columnList = "status,expires_at"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pending_assistant_action_task",
                columnNames = "task_run_id"))
public class PendingAssistantAction extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_run_id", nullable = false)
    private AssistantPlanRun planRun;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "task_run_id", nullable = false)
    private AssistantTaskRun taskRun;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AssistantSession session;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AssistantAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PendingActionStatus status;

    @Column(nullable = false, length = 128)
    private String targetRef;

    @Column(nullable = false, columnDefinition = "text")
    private String argumentsJson;

    @Column(nullable = false, columnDefinition = "text")
    private String previewJson;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant confirmedAt;

    private Instant executedAt;

    private Instant rejectedAt;

    @Column(length = 1000)
    private String resultMessage;

    @Version
    private Long version;

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

    public AssistantSession getSession() {
        return session;
    }

    public void setSession(AssistantSession session) {
        this.session = session;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public AssistantAction getAction() {
        return action;
    }

    public void setAction(AssistantAction action) {
        this.action = action;
    }

    public PendingActionStatus getStatus() {
        return status;
    }

    public void setStatus(PendingActionStatus status) {
        this.status = status;
    }

    public String getTargetRef() {
        return targetRef;
    }

    public void setTargetRef(String targetRef) {
        this.targetRef = targetRef;
    }

    public String getArgumentsJson() {
        return argumentsJson;
    }

    public void setArgumentsJson(String argumentsJson) {
        this.argumentsJson = argumentsJson;
    }

    public String getPreviewJson() {
        return previewJson;
    }

    public void setPreviewJson(String previewJson) {
        this.previewJson = previewJson;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(Instant rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public void setResultMessage(String resultMessage) {
        this.resultMessage = resultMessage;
    }

    public Long getVersion() {
        return version;
    }
}

package com.aishop.domain;

import java.time.Instant;

import com.aishop.assistant.state.AgentRunStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "assistant_plan_runs",
        indexes = @Index(
                name = "idx_assistant_plan_run_waiting",
                columnList = "session_id,user_id,status,created_at"))
public class AssistantPlanRun extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AssistantSession session;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentRunStatus status;

    @Column(nullable = false, columnDefinition = "text")
    private String planJson;

    @Column(nullable = false, length = 32)
    private String plannerSource;

    @Column(length = 64)
    private String promptVersion;

    @Column(length = 64)
    private String modelName;

    @Column(length = 64)
    private String currentTaskId;

    private Instant expiresAt;

    private Instant completedAt;

    @Column(length = 1000)
    private String failureReason;

    @Version
    private Long version;

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

    public AgentRunStatus getStatus() {
        return status;
    }

    public void setStatus(AgentRunStatus status) {
        this.status = status;
    }

    public String getPlanJson() {
        return planJson;
    }

    public void setPlanJson(String planJson) {
        this.planJson = planJson;
    }

    public String getPlannerSource() {
        return plannerSource;
    }

    public void setPlannerSource(String plannerSource) {
        this.plannerSource = plannerSource;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getCurrentTaskId() {
        return currentTaskId;
    }

    public void setCurrentTaskId(String currentTaskId) {
        this.currentTaskId = currentTaskId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Long getVersion() {
        return version;
    }
}

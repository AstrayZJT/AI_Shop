package com.aishop.domain;

import java.time.Instant;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.model.AssistantIntent;
import com.aishop.assistant.model.ExecutionMode;
import com.aishop.assistant.state.AgentRunStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "assistant_task_runs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_assistant_task_run_plan_task",
                columnNames = {"plan_run_id", "task_id"}))
public class AssistantTaskRun extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_run_id", nullable = false)
    private AssistantPlanRun planRun;

    @Column(name = "task_order", nullable = false)
    private Integer taskOrder;

    @Column(name = "task_id", nullable = false, length = 32)
    private String taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AssistantIntent intent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AssistantAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExecutionMode executionMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentRunStatus status;

    @Column(nullable = false, columnDefinition = "text")
    private String taskJson;

    @Column(columnDefinition = "text")
    private String resultJson;

    @Column(length = 128)
    private String toolName;

    @Column(length = 128)
    private String targetRef;

    @Column(length = 1000)
    private String resultMessage;

    private Instant startedAt;

    private Instant completedAt;

    @Version
    private Long version;

    public AssistantPlanRun getPlanRun() {
        return planRun;
    }

    public void setPlanRun(AssistantPlanRun planRun) {
        this.planRun = planRun;
    }

    public Integer getTaskOrder() {
        return taskOrder;
    }

    public void setTaskOrder(Integer taskOrder) {
        this.taskOrder = taskOrder;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public AssistantIntent getIntent() {
        return intent;
    }

    public void setIntent(AssistantIntent intent) {
        this.intent = intent;
    }

    public AssistantAction getAction() {
        return action;
    }

    public void setAction(AssistantAction action) {
        this.action = action;
    }

    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(ExecutionMode executionMode) {
        this.executionMode = executionMode;
    }

    public AgentRunStatus getStatus() {
        return status;
    }

    public void setStatus(AgentRunStatus status) {
        this.status = status;
    }

    public String getTaskJson() {
        return taskJson;
    }

    public void setTaskJson(String taskJson) {
        this.taskJson = taskJson;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
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

    public String getResultMessage() {
        return resultMessage;
    }

    public void setResultMessage(String resultMessage) {
        this.resultMessage = resultMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Long getVersion() {
        return version;
    }
}

package com.aishop.assistant.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.aishop.assistant.answer.AssistantComposedAnswer;
import com.aishop.assistant.answer.AssistantAnswerComposer;
import com.aishop.assistant.context.AssistantContext;
import com.aishop.assistant.context.AssistantContextBuilder;
import com.aishop.assistant.context.ConversationPlanResolver;
import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.orchestration.ToolPlanExecutionResult;
import com.aishop.assistant.rag.KnowledgeRetrievalResult;
import com.aishop.assistant.rag.RagAnswerComposer;
import com.aishop.assistant.rag.RagAnswerResult;
import com.aishop.assistant.tool.TaskToolResult;
import com.aishop.assistant.tool.ToolExecutionStatus;
import com.aishop.assistant.state.AssistantStateMachine;
import com.aishop.assistant.state.StateMachineExecution;
import com.aishop.domain.AppUser;
import com.aishop.domain.AssistantSession;
import com.aishop.assistant.planner.PlannerFacade;

@Service
public class AssistantAgentService {

    private final AssistantContextBuilder contextBuilder;
    private final PlannerFacade plannerFacade;
    private final ConversationPlanResolver planResolver;
    private final RagAnswerComposer ragAnswerComposer;
    private final AssistantAnswerComposer answerComposer;
    private final AssistantStateMachine stateMachine;

    public AssistantAgentService(AssistantContextBuilder contextBuilder,
                                 PlannerFacade plannerFacade,
                                 ConversationPlanResolver planResolver,
                                 RagAnswerComposer ragAnswerComposer,
                                 AssistantAnswerComposer answerComposer,
                                 AssistantStateMachine stateMachine) {
        this.contextBuilder = contextBuilder;
        this.plannerFacade = plannerFacade;
        this.planResolver = planResolver;
        this.ragAnswerComposer = ragAnswerComposer;
        this.answerComposer = answerComposer;
        this.stateMachine = stateMachine;
    }

    public AssistantAgentTurn handle(AppUser user, AssistantSession session, String message) {
        long startedAt = System.nanoTime();
        var waiting = stateMachine.findWaiting(user, session);
        String unfinishedPlanSummary = waiting.map(value -> value.planSummary()).orElse(null);
        AssistantContext context = contextBuilder.build(
                user, session, message, unfinishedPlanSummary);
        StateMachineExecution workflow;
        long planningLatencyMs = 0;
        long executionLatencyMs;
        if (waiting.isPresent()) {
            long executionStartedAt = System.nanoTime();
            workflow = stateMachine.resume(user, session, message);
            executionLatencyMs = elapsedMs(executionStartedAt);
        } else {
            long planningStartedAt = System.nanoTime();
            var planned = plannerFacade.plan(context.toPlannerInput());
            var resolvedPlan = planResolver.resolve(context, planned);
            planningLatencyMs = elapsedMs(planningStartedAt);
            long executionStartedAt = System.nanoTime();
            workflow = stateMachine.start(user, session, resolvedPlan);
            executionLatencyMs = elapsedMs(executionStartedAt);
        }
        ToolPlanExecutionResult execution = workflow.execution();
        long answerStartedAt = System.nanoTime();
        RagAnswerResult ragAnswer = composeRagAnswer(execution);
        var answer = answerComposer.compose(context, execution, ragAnswer);
        long answerLatencyMs = elapsedMs(answerStartedAt);
        AgentTrace trace = trace(
                session, workflow, execution, ragAnswer, answer,
                elapsedMs(startedAt), planningLatencyMs, executionLatencyMs, answerLatencyMs);
        return new AssistantAgentTurn(context, execution, ragAnswer, answer, workflow, trace);
    }

    private AgentTrace trace(AssistantSession session,
                             StateMachineExecution workflow,
                             ToolPlanExecutionResult execution,
                             RagAnswerResult ragAnswer,
                             AssistantComposedAnswer answer,
                             long totalLatencyMs,
                             long planningLatencyMs,
                             long executionLatencyMs,
                             long answerLatencyMs) {
        var planner = execution.planner();
        List<AgentTaskTrace> tasks = execution.taskResults().stream()
                .map(result -> new AgentTaskTrace(
                        result.taskId(),
                        result.action().name(),
                        result.toolName(),
                        result.status().name(),
                        result.targetRef(),
                        clip(result.message(), 160)))
                .toList();
        return new AgentTrace(
                "agent-" + UUID.randomUUID(),
                session.getId(),
                workflow.planRunId(),
                workflow.resumed(),
                planner.source().name(),
                planner.fallbackReason() == null ? null : planner.fallbackReason().name(),
                planner.modelName(),
                planner.inputTokens(),
                planner.outputTokens(),
                totalLatencyMs,
                planningLatencyMs,
                executionLatencyMs,
                answerLatencyMs,
                tasks.size(),
                tasks,
                ragAnswer == null ? null : ragAnswer.mode().name(),
                ragAnswer == null ? 0 : ragAnswer.citations().size(),
                answer.mode(),
                workflow.status().name());
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private String clip(String value, int maximum) {
        if (value == null || value.length() <= maximum) {
            return value;
        }
        return value.substring(0, maximum);
    }

    private RagAnswerResult composeRagAnswer(ToolPlanExecutionResult execution) {
        for (TaskToolResult result : execution.taskResults()) {
            if (result.action() != AssistantAction.SEARCH_KNOWLEDGE
                    || result.status() != ToolExecutionStatus.SUCCEEDED) {
                continue;
            }
            Object retrieval = result.data().get("retrieval");
            if (retrieval instanceof KnowledgeRetrievalResult value) {
                String query = String.valueOf(result.arguments().getOrDefault("query", value.query()));
                return ragAnswerComposer.compose(query, value);
            }
        }
        return null;
    }
}

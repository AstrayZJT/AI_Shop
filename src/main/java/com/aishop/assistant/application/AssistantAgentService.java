package com.aishop.assistant.application;

import org.springframework.stereotype.Service;

import com.aishop.assistant.answer.AssistantAnswerComposer;
import com.aishop.assistant.context.AssistantContext;
import com.aishop.assistant.context.AssistantContextBuilder;
import com.aishop.assistant.context.ConversationPlanResolver;
import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.orchestration.AssistantToolOrchestrator;
import com.aishop.assistant.orchestration.ToolPlanExecutionResult;
import com.aishop.assistant.rag.KnowledgeRetrievalResult;
import com.aishop.assistant.rag.RagAnswerComposer;
import com.aishop.assistant.rag.RagAnswerResult;
import com.aishop.assistant.tool.TaskToolResult;
import com.aishop.assistant.tool.ToolExecutionStatus;
import com.aishop.domain.AppUser;
import com.aishop.domain.AssistantSession;
import com.aishop.assistant.planner.PlannerFacade;

@Service
public class AssistantAgentService {

    private final AssistantContextBuilder contextBuilder;
    private final PlannerFacade plannerFacade;
    private final ConversationPlanResolver planResolver;
    private final AssistantToolOrchestrator toolOrchestrator;
    private final RagAnswerComposer ragAnswerComposer;
    private final AssistantAnswerComposer answerComposer;

    public AssistantAgentService(AssistantContextBuilder contextBuilder,
                                 PlannerFacade plannerFacade,
                                 ConversationPlanResolver planResolver,
                                 AssistantToolOrchestrator toolOrchestrator,
                                 RagAnswerComposer ragAnswerComposer,
                                 AssistantAnswerComposer answerComposer) {
        this.contextBuilder = contextBuilder;
        this.plannerFacade = plannerFacade;
        this.planResolver = planResolver;
        this.toolOrchestrator = toolOrchestrator;
        this.ragAnswerComposer = ragAnswerComposer;
        this.answerComposer = answerComposer;
    }

    public AssistantAgentTurn handle(AppUser user, AssistantSession session, String message) {
        AssistantContext context = contextBuilder.build(user, session, message, null);
        var planned = plannerFacade.plan(context.toPlannerInput());
        var resolvedPlan = planResolver.resolve(context, planned);
        ToolPlanExecutionResult execution = toolOrchestrator.executePlan(user, resolvedPlan);
        RagAnswerResult ragAnswer = composeRagAnswer(execution);
        var answer = answerComposer.compose(context, execution, ragAnswer);
        return new AssistantAgentTurn(context, execution, ragAnswer, answer);
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

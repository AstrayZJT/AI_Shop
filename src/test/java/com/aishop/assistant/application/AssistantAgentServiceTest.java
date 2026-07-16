package com.aishop.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aishop.assistant.answer.AssistantAnswerComposer;
import com.aishop.assistant.answer.AssistantComposedAnswer;
import com.aishop.assistant.context.AssistantContext;
import com.aishop.assistant.context.AssistantContextBuilder;
import com.aishop.assistant.context.ConversationPlanResolver;
import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.model.AssistantIntent;
import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.AssistantTask;
import com.aishop.assistant.model.ExecutionMode;
import com.aishop.assistant.model.PlanType;
import com.aishop.assistant.model.PlannerResult;
import com.aishop.assistant.model.PlannerSource;
import com.aishop.assistant.orchestration.AssistantToolOrchestrator;
import com.aishop.assistant.orchestration.ToolPlanExecutionResult;
import com.aishop.assistant.planner.PlannerFacade;
import com.aishop.assistant.rag.KnowledgeRetrievalResult;
import com.aishop.assistant.rag.RagAnswerComposer;
import com.aishop.assistant.rag.RagAnswerMode;
import com.aishop.assistant.rag.RagAnswerResult;
import com.aishop.assistant.tool.TaskToolResult;
import com.aishop.assistant.tool.ToolExecutionStatus;
import com.aishop.domain.AppUser;
import com.aishop.domain.AssistantSession;

class AssistantAgentServiceTest {

    @Test
    void connectsContextPlannerToolRagAndAnswerInOneApplicationFlow() {
        AssistantContextBuilder contextBuilder = mock(AssistantContextBuilder.class);
        PlannerFacade plannerFacade = mock(PlannerFacade.class);
        ConversationPlanResolver resolver = mock(ConversationPlanResolver.class);
        AssistantToolOrchestrator orchestrator = mock(AssistantToolOrchestrator.class);
        RagAnswerComposer ragComposer = mock(RagAnswerComposer.class);
        AssistantAnswerComposer answerComposer = mock(AssistantAnswerComposer.class);
        AssistantAgentService service = new AssistantAgentService(
                contextBuilder, plannerFacade, resolver, orchestrator, ragComposer, answerComposer);
        AppUser user = new AppUser();
        user.setId(1L);
        AssistantSession session = new AssistantSession();
        session.setId(2L);
        AssistantContext context = new AssistantContext(
                "七天无理由怎么退货", null, List.of(), List.of(), null, null, 20, 2_000, false);
        PlannerResult planned = planner();
        KnowledgeRetrievalResult retrieval = new KnowledgeRetrievalResult(
                "七天无理由怎么退货", List.of(), "", List.of(), false);
        TaskToolResult toolResult = new TaskToolResult(
                "t1", AssistantAction.SEARCH_KNOWLEDGE, "search_knowledge", ToolExecutionStatus.SUCCEEDED,
                Map.of("query", "七天无理由怎么退货"), null, Map.of("retrieval", retrieval), "检索完成");
        ToolPlanExecutionResult execution = new ToolPlanExecutionResult(planned, List.of(toolResult));
        RagAnswerResult ragAnswer = new RagAnswerResult(
                "知识库证据不足", RagAnswerMode.NO_EVIDENCE, false, List.of(), List.of(), 0,
                false, "test", "NO_EVIDENCE", null, null, null);
        AssistantComposedAnswer composed = new AssistantComposedAnswer(
                "知识库证据不足", "STRUCTURED_EVIDENCE", List.of());
        when(contextBuilder.build(user, session, "七天无理由怎么退货", null)).thenReturn(context);
        when(plannerFacade.plan(context.toPlannerInput())).thenReturn(planned);
        when(resolver.resolve(context, planned)).thenReturn(planned);
        when(orchestrator.executePlan(user, planned)).thenReturn(execution);
        when(ragComposer.compose("七天无理由怎么退货", retrieval)).thenReturn(ragAnswer);
        when(answerComposer.compose(context, execution, ragAnswer)).thenReturn(composed);

        AssistantAgentTurn turn = service.handle(user, session, "七天无理由怎么退货");

        assertThat(turn.composedAnswer().answer()).isEqualTo("知识库证据不足");
        verify(plannerFacade).plan(context.toPlannerInput());
        verify(orchestrator).executePlan(user, planned);
        verify(ragComposer).compose("七天无理由怎么退货", retrieval);
        verify(answerComposer).compose(context, execution, ragAnswer);
    }

    private PlannerResult planner() {
        AssistantTask task = new AssistantTask(
                "t1", AssistantIntent.KNOWLEDGE, AssistantAction.SEARCH_KNOWLEDGE,
                ExecutionMode.TOOL_READ, Map.of("query", "七天无理由怎么退货"),
                List.of(), List.of(), List.of(), 0.9, "test");
        return new PlannerResult(
                new AssistantPlan(PlanType.SINGLE_TASK, List.of(task), "检索规则"),
                PlannerSource.LLM, "test", null, null, "model", null, null);
    }
}

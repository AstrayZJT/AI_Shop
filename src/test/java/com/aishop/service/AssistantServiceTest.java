package com.aishop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.Test;

import com.aishop.assistant.answer.AssistantComposedAnswer;
import com.aishop.assistant.application.AssistantAgentService;
import com.aishop.assistant.application.AssistantAgentTurn;
import com.aishop.assistant.context.AssistantContext;
import com.aishop.assistant.context.AssistantContextBuilder;
import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.model.AssistantIntent;
import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.AssistantTask;
import com.aishop.assistant.model.ExecutionMode;
import com.aishop.assistant.model.PlanType;
import com.aishop.assistant.model.PlannerResult;
import com.aishop.assistant.model.PlannerSource;
import com.aishop.assistant.orchestration.ToolPlanExecutionResult;
import com.aishop.assistant.tool.TaskToolResult;
import com.aishop.assistant.tool.ToolExecutionStatus;
import com.aishop.assistant.state.AgentRunStatus;
import com.aishop.assistant.state.StateMachineExecution;
import com.aishop.domain.AppUser;
import com.aishop.domain.AssistantMessage;
import com.aishop.domain.AssistantSession;
import com.aishop.repository.AssistantMessageRepository;
import com.aishop.repository.AssistantSessionRepository;

class AssistantServiceTest {

    @SuppressWarnings("unchecked")
    @Test
    void formalChatEndpointServiceUsesUnifiedAgentFlowAndPersistsTurn() {
        AssistantSessionRepository sessions = mock(AssistantSessionRepository.class);
        AssistantMessageRepository messages = mock(AssistantMessageRepository.class);
        CompiledGraph<MessagesState<String>> graph = mock(CompiledGraph.class);
        AssistantAgentService agentService = mock(AssistantAgentService.class);
        AssistantContextBuilder contextBuilder = mock(AssistantContextBuilder.class);
        AssistantService service = new AssistantService(sessions, messages, graph, agentService, contextBuilder);
        AppUser user = new AppUser();
        user.setId(1L);
        user.setUsername("student");
        AssistantSession session = new AssistantSession();
        session.setId(10L);
        session.setUser(user);
        session.setSummary("新会话");
        session.setServiceStatus("ACTIVE");
        session.setSupportUnreadCount(0L);
        session.setCustomerUnreadCount(0L);
        when(sessions.findByIdAndUser(10L, user)).thenReturn(Optional.of(session));
        when(sessions.save(any(AssistantSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.save(any(AssistantMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contextBuilder.nextConversationSummary("新会话", "查订单", "请补充订单号。"))
                .thenReturn("bounded summary");
        when(agentService.handle(user, session, "查订单")).thenReturn(turn());

        var response = service.chat(user, 10L, "查订单", null);

        assertThat(response.answer()).isEqualTo("请补充订单号。");
        assertThat(response.intent()).isEqualTo("order");
        assertThat(response.threadId()).isEqualTo("assistant-10");
        assertThat(session.getSummary()).isEqualTo("bounded summary");
        verify(agentService).handle(user, session, "查订单");
        verify(messages, times(2)).save(any(AssistantMessage.class));
    }

    private AssistantAgentTurn turn() {
        AssistantContext context = new AssistantContext(
                "查订单", null, List.of(), List.of(), null, null, 3, 2_000, false);
        AssistantTask task = new AssistantTask(
                "t1", AssistantIntent.ORDER, AssistantAction.QUERY_ORDER, ExecutionMode.TOOL_READ,
                Map.of(), List.of("orderNo"), List.of(), List.of(), 0.9, "test");
        PlannerResult planner = new PlannerResult(
                new AssistantPlan(PlanType.SINGLE_TASK, List.of(task), "查订单"),
                PlannerSource.LLM, "test", null, null, "model", null, null);
        TaskToolResult result = new TaskToolResult(
                "t1", AssistantAction.QUERY_ORDER, null, ToolExecutionStatus.NEEDS_INPUT,
                Map.of(), null, Map.of("missingSlots", List.of("orderNo")), "missing");
        ToolPlanExecutionResult execution = new ToolPlanExecutionResult(planner, List.of(result));
        return new AssistantAgentTurn(
                context, execution, null,
                new AssistantComposedAnswer("请补充订单号。", "STRUCTURED_EVIDENCE", List.of()),
                new StateMachineExecution(1L, AgentRunStatus.WAITING_INPUT, null, false, execution));
    }
}

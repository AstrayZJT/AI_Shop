package com.aishop.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.aishop.assistant.guardrail.AgentAccessDeniedException;
import com.aishop.assistant.state.AgentRunStatus;
import com.aishop.assistant.state.AssistantStateMachine;
import com.aishop.assistant.state.StateMachineExecution;
import com.aishop.domain.AppUser;
import com.aishop.dto.AssistantDtos.PendingActionResponse;
import com.aishop.service.AssistantPendingActionService;
import com.aishop.service.AssistantRuntimeStatusService;
import com.aishop.service.AssistantService;
import com.aishop.service.AuthService;

class AssistantPendingActionControllerTest {

    private AuthService authService;
    private AssistantPendingActionService pendingActionService;
    private AssistantStateMachine stateMachine;
    private AppUser user;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        pendingActionService = mock(AssistantPendingActionService.class);
        stateMachine = mock(AssistantStateMachine.class);
        user = new AppUser();
        user.setId(7L);
        when(authService.requireUser(any())).thenReturn(user);
        AssistantController controller = new AssistantController(
                authService,
                mock(AssistantService.class),
                mock(AssistantRuntimeStatusService.class),
                pendingActionService,
                stateMachine);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void confirmsPendingActionWithClientRequestId() throws Exception {
        StateMachineExecution execution = mock(StateMachineExecution.class);
        when(execution.planRunId()).thenReturn(31L);
        when(execution.status()).thenReturn(AgentRunStatus.SUCCEEDED);
        when(execution.idempotentReplay()).thenReturn(false);
        when(stateMachine.confirmPendingAction(user, 11L, 22L, "request-12345678"))
                .thenReturn(execution);
        when(pendingActionService.get(user, 11L, 22L)).thenReturn(response());

        mvc.perform(post("/api/assistant/sessions/11/pending-actions/22/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestId\":\"request-12345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingAction.status").value("EXECUTED"))
                .andExpect(jsonPath("$.runStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.idempotentReplay").value(false));

        verify(stateMachine).confirmPendingAction(user, 11L, 22L, "request-12345678");
    }

    @Test
    void rejectsMalformedIdempotencyKeyBeforeStateMachine() throws Exception {
        mvc.perform(post("/api/assistant/sessions/11/pending-actions/22/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestId\":\"bad key\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsCrossUserConfirmationToForbidden() throws Exception {
        when(stateMachine.confirmPendingAction(eq(user), eq(11L), eq(22L), any()))
                .thenThrow(new AgentAccessDeniedException("无权操作其他用户的待确认动作"));

        mvc.perform(post("/api/assistant/sessions/11/pending-actions/22/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientRequestId\":\"request-attacker\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("无权操作其他用户的待确认动作"));
    }

    private PendingActionResponse response() {
        return new PendingActionResponse(
                22L, 11L, 31L, "t1", "CANCEL_ORDER", "EXECUTED", "ORD-12345678",
                Map.of("currentStatus", "PENDING_PAYMENT"), Instant.now().plusSeconds(60),
                Instant.now(), Instant.now(), null, "request-12345678", "订单取消成功", Instant.now());
    }
}

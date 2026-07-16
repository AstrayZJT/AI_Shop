package com.aishop.assistant.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.tool.ToolExecutionStatus;

class ActionPolicyRegistryTest {

    private final ActionPolicyRegistry registry = new ActionPolicyRegistry();

    @Test
    void coversEveryAssistantActionExactlyOnce() {
        assertThat(registry.all()).extracting(ActionPolicy::action)
                .containsExactlyInAnyOrder(AssistantAction.values());
    }

    @Test
    void onlyCancelOrderCanExecuteAfterConfirmationInCurrentStage() {
        assertThat(registry.all().stream()
                .filter(ActionPolicy::confirmationExecutionEnabled)
                .map(ActionPolicy::action))
                .containsExactly(AssistantAction.CANCEL_ORDER);
        assertThat(registry.requireConfirmationExecution(AssistantAction.CANCEL_ORDER).action())
                .isEqualTo(AssistantAction.CANCEL_ORDER);
        assertThatThrownBy(() -> registry.requireConfirmationExecution(AssistantAction.REQUEST_REFUND))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void blocksHighRiskSuccessBeforeConfirmationAndReadOnlyPreparation() {
        EnumSet<AssistantAction> highRisk = EnumSet.noneOf(AssistantAction.class);
        registry.all().stream()
                .filter(ActionPolicy::confirmationRequired)
                .map(ActionPolicy::action)
                .forEach(highRisk::add);

        for (AssistantAction action : highRisk) {
            assertThatThrownBy(() -> registry.validateToolResult(action, ToolExecutionStatus.SUCCEEDED))
                    .isInstanceOf(SecurityException.class);
        }
        assertThatThrownBy(() -> registry.validateToolResult(
                AssistantAction.QUERY_ORDER, ToolExecutionStatus.PREPARED))
                .isInstanceOf(SecurityException.class);
    }
}

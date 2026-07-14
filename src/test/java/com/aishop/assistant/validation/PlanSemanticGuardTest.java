package com.aishop.assistant.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.model.AssistantIntent;
import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.AssistantTask;
import com.aishop.assistant.model.ExecutionMode;
import com.aishop.assistant.model.PlanType;
import com.aishop.assistant.model.PlannerInput;

class PlanSemanticGuardTest {

    private final PlanSemanticGuard guard = new PlanSemanticGuard();

    @Test
    void rejectsWriteActionForPurePolicyConsultation() {
        AssistantPlan plan = cancelPlan();

        assertThatThrownBy(() -> guard.validate(
                new PlannerInput("怎么取消订单，需要满足什么规则？", null, null), plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("纯咨询消息不能规划写操作");
    }

    @Test
    void allowsWritePlanWhenUserExplicitlyRequestsExecution() {
        AssistantPlan plan = cancelPlan();

        assertThatCode(() -> guard.validate(
                new PlannerInput("可以帮我取消订单 ORD-12345678 吗？", null, null), plan))
                .doesNotThrowAnyException();
    }

    private AssistantPlan cancelPlan() {
        var task = new AssistantTask(
                "t1",
                AssistantIntent.ORDER,
                AssistantAction.CANCEL_ORDER,
                ExecutionMode.ASK_CONFIRM,
                Map.of("orderNo", "ORD-12345678"),
                List.of(),
                List.of(),
                List.of(),
                0.9,
                "cancel");
        return new AssistantPlan(PlanType.SINGLE_TASK, List.of(task), "cancel");
    }
}

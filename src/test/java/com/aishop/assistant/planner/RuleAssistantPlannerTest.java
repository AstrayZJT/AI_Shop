package com.aishop.assistant.planner;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.model.ExecutionMode;
import com.aishop.assistant.model.PlanType;
import com.aishop.assistant.model.PlannerInput;
import com.aishop.assistant.validation.PlanValidator;

class RuleAssistantPlannerTest {

    private final RuleAssistantPlanner planner = new RuleAssistantPlanner();
    private final PlanValidator validator = new PlanValidator();

    @Test
    void plansLogisticsThenCancelAsTwoDependentTasks() {
        var plan = plan("查一下订单 ORD-12345678 的物流，如果还没发货就帮我取消");

        assertThat(plan.planType()).isEqualTo(PlanType.MULTI_TASK);
        assertThat(plan.tasks()).extracting(task -> task.action())
                .containsExactly(AssistantAction.QUERY_LOGISTICS, AssistantAction.CANCEL_ORDER);
        assertThat(plan.tasks().get(1).dependsOn()).containsExactly("t1");
        assertThat(plan.tasks().get(1).conditions()).hasSize(1);
    }

    @Test
    void plansCancelAsConfirmation() {
        var plan = plan("帮我取消订单 ORD-12345678");

        assertThat(plan.tasks().getFirst().action()).isEqualTo(AssistantAction.CANCEL_ORDER);
        assertThat(plan.tasks().getFirst().executionMode()).isEqualTo(ExecutionMode.ASK_CONFIRM);
        assertThat(plan.tasks().getFirst().slots()).containsEntry("orderNo", "ORD-12345678");
    }

    @Test
    void marksOrderNumberMissingInsteadOfGuessing() {
        var plan = plan("帮我取消订单");

        assertThat(plan.tasks().getFirst().action()).isEqualTo(AssistantAction.CANCEL_ORDER);
        assertThat(plan.tasks().getFirst().missingSlots()).containsExactly("orderNo");
    }

    @Test
    void treatsHowToCancelAsKnowledgeQuestion() {
        var plan = plan("怎么取消订单，有什么规则？");

        assertThat(plan.tasks().getFirst().action()).isEqualTo(AssistantAction.SEARCH_KNOWLEDGE);
        assertThat(plan.tasks().getFirst().executionMode()).isEqualTo(ExecutionMode.TOOL_READ);
    }

    @Test
    void splitsProductRecommendationAndPolicyQuestion() {
        var plan = plan("推荐一款 500 元以内的耳机，不合适能不能七天无理由退货");

        assertThat(plan.planType()).isEqualTo(PlanType.MULTI_TASK);
        assertThat(plan.tasks()).extracting(task -> task.action())
                .containsExactly(AssistantAction.SEARCH_PRODUCT, AssistantAction.SEARCH_KNOWLEDGE);
        assertThat(plan.tasks().getFirst().slots()).containsEntry("budgetMax", 500);
    }

    @Test
    void plansPromotionQuery() {
        var plan = plan("现在有什么满减优惠活动？");

        assertThat(plan.tasks().getFirst().action()).isEqualTo(AssistantAction.CHECK_PROMOTION);
    }

    @Test
    void plansAddressUpdateAndExtractsAddress() {
        var plan = plan("把订单 ORD-12345678 的地址改成上海市浦东新区测试路 1 号");

        assertThat(plan.tasks().getFirst().action()).isEqualTo(AssistantAction.UPDATE_ADDRESS);
        assertThat(plan.tasks().getFirst().slots())
                .containsEntry("orderNo", "ORD-12345678")
                .containsEntry("address", "上海市浦东新区测试路 1 号");
    }

    @Test
    void plansHumanHandoffAsConfirmation() {
        var plan = plan("帮我转人工客服");

        assertThat(plan.tasks().getFirst().action()).isEqualTo(AssistantAction.HANDOFF);
        assertThat(plan.tasks().getFirst().executionMode()).isEqualTo(ExecutionMode.ASK_CONFIRM);
    }

    @Test
    void plansPaymentAsConfirmation() {
        var plan = plan("帮我支付订单 ORD-12345678");

        assertThat(plan.tasks().getFirst().action()).isEqualTo(AssistantAction.PAY_ORDER);
        assertThat(plan.tasks().getFirst().executionMode()).isEqualTo(ExecutionMode.ASK_CONFIRM);
    }

    @Test
    void fallsBackToGeneralChatForUnrecognizedText() {
        var plan = plan("你好，今天过得怎么样？");

        assertThat(plan.tasks().getFirst().action()).isEqualTo(AssistantAction.GENERAL_CHAT);
        assertThat(plan.tasks().getFirst().executionMode()).isEqualTo(ExecutionMode.ANSWER_ONLY);
    }

    private com.aishop.assistant.model.AssistantPlan plan(String message) {
        return validator.validate(planner.plan(new PlannerInput(message, null, null)));
    }
}

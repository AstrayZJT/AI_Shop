package com.aishop.assistant.planner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.model.AssistantIntent;
import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.AssistantTask;
import com.aishop.assistant.model.ConditionField;
import com.aishop.assistant.model.ConditionOperator;
import com.aishop.assistant.model.ExecutionMode;
import com.aishop.assistant.model.PlanType;
import com.aishop.assistant.model.PlannerInput;
import com.aishop.assistant.model.TaskCondition;

@Component
public class RuleAssistantPlanner {

    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("ORD-[A-Z0-9]{8}", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUDGET_PATTERN = Pattern.compile("(\\d{2,6})\\s*元?(?:以内|以下|之内)");
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("(?:改成|改为|修改为|修改成|更新为|更新成|地址为)(?<address>.+)$");

    public AssistantPlan plan(PlannerInput input) {
        String message = input.message() == null ? "" : input.message().trim();
        String text = message.toLowerCase();
        if (text.isBlank()) {
            return clarification("请告诉我你想咨询什么问题", "message");
        }

        boolean consultation = asksHowOrPolicy(text);
        boolean logistics = containsAny(text, "物流", "快递", "运单", "派送", "配送", "到哪", "揽收", "签收", "发货");
        boolean cancel = text.contains("取消订单") || (text.contains("取消") && text.contains("订单"));
        if (logistics && cancel && !consultation) {
            return logisticsThenCancel(message);
        }

        boolean product = containsAny(text, "推荐", "商品", "手机", "耳机", "平板", "电脑", "键盘");
        boolean policy = containsAny(text, "规则", "政策", "七天", "无理由", "保修", "退货", "退款", "发票", "开票");
        if (product && policy) {
            return productAndKnowledge(message);
        }

        if (containsAny(text, "转人工", "转接人工", "人工客服", "真人客服", "客服介入") && !consultation) {
            return single(task(
                    "t1", AssistantIntent.HANDOFF, AssistantAction.HANDOFF, ExecutionMode.ASK_CONFIRM,
                    Map.of("reason", slotText(message)), List.of(), List.of(), List.of(), 0.86, "用户明确申请人工客服"),
                    "申请转接人工客服");
        }

        if (consultation && containsAny(text, "取消", "退款", "退货", "支付", "地址", "发票", "保修")) {
            return single(searchKnowledgeTask("t1", message, List.of()), "查询业务规则");
        }
        if (cancel) {
            return single(orderActionTask("t1", AssistantAction.CANCEL_ORDER, message), "申请取消订单");
        }
        if (containsAny(text, "确认收货", "收货确认")) {
            return single(orderActionTask("t1", AssistantAction.CONFIRM_RECEIPT, message), "确认订单收货");
        }
        if (containsAny(text, "退款", "退货", "售后")) {
            return single(orderActionTask("t1", AssistantAction.REQUEST_REFUND, message), "申请退款或售后");
        }
        if (containsAny(text, "支付", "付款", "付钱", "补款", "结清")) {
            return single(orderActionTask("t1", AssistantAction.PAY_ORDER, message), "支付订单");
        }
        if (text.contains("地址") && containsAny(text, "修改", "更改", "更新", "改成", "改为")) {
            return single(updateAddressTask(message), "修改订单收货地址");
        }
        if (logistics) {
            return single(queryOrderTask("t1", AssistantAction.QUERY_LOGISTICS, message, List.of()), "查询订单物流");
        }
        if (text.contains("订单")) {
            return single(queryOrderTask("t1", AssistantAction.QUERY_ORDER, message, List.of()), "查询订单");
        }
        if (containsAny(text, "优惠", "优惠券", "优惠码", "满减", "折扣", "促销", "活动")) {
            return single(task(
                    "t1", AssistantIntent.PROMOTION, AssistantAction.CHECK_PROMOTION, ExecutionMode.TOOL_READ,
                    Map.of("query", slotText(message)), List.of(), List.of(), List.of(), 0.78, "本地规则识别为优惠查询"),
                    "查询优惠活动");
        }
        if (containsAny(text, "下单", "购买", "来一单", "结算") || text.startsWith("买")) {
            return single(createDraftTask(message), "创建订单草稿");
        }
        if (product) {
            return single(searchProductTask("t1", message), "搜索或推荐商品");
        }
        if (policy || containsAny(text, "faq", "说明")) {
            return single(searchKnowledgeTask("t1", message, List.of()), "查询知识库");
        }
        return single(task(
                "t1", AssistantIntent.CHAT, AssistantAction.GENERAL_CHAT, ExecutionMode.ANSWER_ONLY,
                Map.of("query", slotText(message)), List.of(), List.of(), List.of(), 0.60, "本地规则未识别到具体业务动作"),
                "普通对话");
    }

    private AssistantPlan logisticsThenCancel(String message) {
        AssistantTask logistics = queryOrderTask("t1", AssistantAction.QUERY_LOGISTICS, message, List.of());
        Map<String, Object> slots = orderSlots(message);
        List<String> missingSlots = requiredIfMissing(slots, "orderNo");
        TaskCondition condition = new TaskCondition(
                "t1",
                ConditionField.ORDER_STATUS,
                ConditionOperator.IN,
                List.of("PENDING_PAYMENT", "CONFIRMED", "PROCESSING"));
        AssistantTask cancel = task(
                "t2", AssistantIntent.ORDER, AssistantAction.CANCEL_ORDER, ExecutionMode.ASK_CONFIRM,
                slots, missingSlots, List.of("t1"), List.of(condition), 0.82,
                "用户要求在订单未发货时取消");
        return new AssistantPlan(PlanType.MULTI_TASK, List.of(logistics, cancel), "先查询物流，满足条件后申请取消订单");
    }

    private AssistantPlan productAndKnowledge(String message) {
        AssistantTask product = searchProductTask("t1", message);
        AssistantTask knowledge = searchKnowledgeTask("t2", message, List.of());
        return new AssistantPlan(
                PlanType.MULTI_TASK,
                List.of(product, knowledge),
                "搜索商品并查询相关售后规则");
    }

    private AssistantTask queryOrderTask(String taskId,
                                         AssistantAction action,
                                         String message,
                                         List<String> dependsOn) {
        Map<String, Object> slots = orderSlots(message);
        return task(
                taskId, AssistantIntent.ORDER, action, ExecutionMode.TOOL_READ,
                slots, requiredIfMissing(slots, "orderNo"), dependsOn, List.of(), 0.80,
                action == AssistantAction.QUERY_LOGISTICS ? "本地规则识别为物流查询" : "本地规则识别为订单查询");
    }

    private AssistantTask orderActionTask(String taskId, AssistantAction action, String message) {
        Map<String, Object> slots = orderSlots(message);
        if (action == AssistantAction.REQUEST_REFUND) {
            slots.put("reason", slotText(message));
        }
        AssistantIntent intent = action == AssistantAction.REQUEST_REFUND
                ? AssistantIntent.AFTER_SALES
                : AssistantIntent.ORDER;
        return task(
                taskId, intent, action, ExecutionMode.ASK_CONFIRM,
                slots, requiredIfMissing(slots, "orderNo"), List.of(), List.of(), 0.76,
                "本地规则识别到需要确认的订单动作");
    }

    private AssistantTask updateAddressTask(String message) {
        Map<String, Object> slots = orderSlots(message);
        Matcher matcher = ADDRESS_PATTERN.matcher(message);
        if (matcher.find() && !matcher.group("address").isBlank()) {
            slots.put("address", slotText(matcher.group("address").trim()));
        }
        List<String> missing = new ArrayList<>(requiredIfMissing(slots, "orderNo"));
        if (!slots.containsKey("address")) {
            missing.add("address");
        }
        return task(
                "t1", AssistantIntent.PROFILE, AssistantAction.UPDATE_ADDRESS, ExecutionMode.ASK_CONFIRM,
                slots, missing, List.of(), List.of(), 0.78, "本地规则识别为修改订单地址");
    }

    private AssistantTask createDraftTask(String message) {
        return task(
                "t1", AssistantIntent.ORDER, AssistantAction.CREATE_ORDER_DRAFT, ExecutionMode.CREATE_DRAFT,
                Map.of("productQuery", slotText(message)), List.of(), List.of(), List.of(), 0.72,
                "本地规则识别为购买或下单请求");
    }

    private AssistantTask searchProductTask(String taskId, String message) {
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("query", slotText(message));
        Matcher matcher = BUDGET_PATTERN.matcher(message);
        if (matcher.find()) {
            slots.put("budgetMax", Integer.parseInt(matcher.group(1)));
        }
        return task(
                taskId, AssistantIntent.PRODUCT, AssistantAction.SEARCH_PRODUCT, ExecutionMode.TOOL_READ,
                slots, List.of(), List.of(), List.of(), 0.78, "本地规则识别为商品搜索或推荐");
    }

    private AssistantTask searchKnowledgeTask(String taskId, String message, List<String> dependsOn) {
        return task(
                taskId, AssistantIntent.KNOWLEDGE, AssistantAction.SEARCH_KNOWLEDGE, ExecutionMode.TOOL_READ,
                Map.of("query", slotText(message)), List.of(), dependsOn, List.of(), 0.75,
                "本地规则识别为知识或规则查询");
    }

    private AssistantPlan clarification(String question, String missingSlot) {
        AssistantTask task = task(
                "t1", AssistantIntent.CHAT, AssistantAction.ASK_CLARIFICATION, ExecutionMode.CLARIFY,
                Map.of("question", question), List.of(), List.of(), List.of(), 1.0,
                "输入为空，需要补充问题");
        return new AssistantPlan(PlanType.CLARIFY, List.of(task), question);
    }

    private AssistantPlan single(AssistantTask task, String summary) {
        return new AssistantPlan(PlanType.SINGLE_TASK, List.of(task), summary);
    }

    private AssistantTask task(String taskId,
                               AssistantIntent intent,
                               AssistantAction action,
                               ExecutionMode executionMode,
                               Map<String, Object> slots,
                               List<String> missingSlots,
                               List<String> dependsOn,
                               List<TaskCondition> conditions,
                               double confidence,
                               String reason) {
        return new AssistantTask(
                taskId, intent, action, executionMode, slots, missingSlots,
                dependsOn, conditions, confidence, reason);
    }

    private Map<String, Object> orderSlots(String message) {
        Map<String, Object> slots = new LinkedHashMap<>();
        Matcher matcher = ORDER_NO_PATTERN.matcher(message == null ? "" : message);
        if (matcher.find()) {
            slots.put("orderNo", matcher.group().toUpperCase());
        }
        return slots;
    }

    private List<String> requiredIfMissing(Map<String, Object> slots, String name) {
        return slots.containsKey(name) ? List.of() : List.of(name);
    }

    private boolean asksHowOrPolicy(String text) {
        return containsAny(text, "怎么", "如何", "能不能", "可以吗", "可不可以", "是什么", "规则", "政策", "支持吗");
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String slotText(String value) {
        if (value == null || value.length() <= 512) {
            return value;
        }
        return value.substring(0, 512);
    }
}

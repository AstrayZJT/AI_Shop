package com.aishop.assistant.tool.tools;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.tool.AssistantTool;
import com.aishop.assistant.tool.PreparedToolCall;
import com.aishop.assistant.tool.ToolArguments;
import com.aishop.assistant.tool.ToolContext;
import com.aishop.assistant.tool.ToolExecutionOutcome;
import com.aishop.assistant.tool.ToolPolicy;
import com.aishop.assistant.tool.ToolRiskLevel;
import com.aishop.dto.OrderDtos.OrderResponse;
import com.aishop.service.OrderService;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

@Component
public class PrepareCancelOrderTool implements AssistantTool {

    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("orderNo", "note");
    private static final ToolPolicy POLICY = new ToolPolicy(
            "prepare_cancel_order",
            "只校验并准备取消当前登录用户自己的订单，不会真正取消。后续必须由用户确认。",
            AssistantAction.CANCEL_ORDER,
            ToolRiskLevel.PREPARE_ONLY,
            false);
    private static final ToolSpecification SPECIFICATION = ToolSpecification.builder()
            .name(POLICY.name())
            .description(POLICY.description())
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("orderNo", "准备取消的订单号")
                    .addStringProperty("note", "可选取消原因")
                    .required("orderNo")
                    .additionalProperties(false)
                    .build())
            .strict(true)
            .build();

    private final OrderService orderService;

    public PrepareCancelOrderTool(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public ToolPolicy policy() {
        return POLICY;
    }

    @Override
    public ToolSpecification specification() {
        return SPECIFICATION;
    }

    @Override
    public PreparedToolCall prepare(ToolContext context, Map<String, Object> arguments) {
        ToolArguments.rejectUnknown(arguments, ALLOWED_ARGUMENTS);
        String orderNo = ToolArguments.requireString(arguments, "orderNo", 64).toUpperCase();
        String note = ToolArguments.optionalString(arguments, "note", 512);
        OrderResponse order = orderService.findByOrderNo(context.user(), orderNo);
        if (!canCancel(order.status())) {
            throw new IllegalArgumentException("订单当前状态不支持取消: " + order.status());
        }
        Map<String, Object> normalized = note == null
                ? Map.of("orderNo", orderNo)
                : Map.of("orderNo", orderNo, "note", note);
        return new PreparedToolCall(
                POLICY.action(),
                POLICY.name(),
                POLICY.riskLevel(),
                normalized,
                orderNo,
                Map.of(
                        "orderId", order.id(),
                        "orderNo", order.orderNo(),
                        "currentStatus", order.status(),
                        "requiresConfirmation", true,
                        "effect", "确认后订单状态将变更为 CANCELLED"));
    }

    @Override
    public ToolExecutionOutcome execute(ToolContext context, PreparedToolCall call) {
        throw new SecurityException("prepare_cancel_order 是 PREPARE_ONLY 工具，禁止自动执行取消订单");
    }

    private boolean canCancel(String status) {
        return "PENDING_PAYMENT".equals(status)
                || "CONFIRMED".equals(status)
                || "PROCESSING".equals(status);
    }
}

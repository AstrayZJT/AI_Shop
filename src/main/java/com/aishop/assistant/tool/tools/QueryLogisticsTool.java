package com.aishop.assistant.tool.tools;

import java.time.Instant;
import java.util.List;
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
public class QueryLogisticsTool implements AssistantTool {

    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("orderNo");
    private static final ToolPolicy POLICY = new ToolPolicy(
            "query_logistics",
            "查询当前登录用户自己的订单状态、承运商、运单号和最近物流节点。",
            AssistantAction.QUERY_LOGISTICS,
            ToolRiskLevel.READ_ONLY,
            true);
    private static final ToolSpecification SPECIFICATION = ToolSpecification.builder()
            .name(POLICY.name())
            .description(POLICY.description())
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("orderNo", "要查询物流的订单号")
                    .required("orderNo")
                    .additionalProperties(false)
                    .build())
            .strict(true)
            .build();

    private final OrderService orderService;

    public QueryLogisticsTool(OrderService orderService) {
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
        OrderResponse order = orderService.findByOrderNo(context.user(), orderNo);
        return new PreparedToolCall(
                POLICY.action(), POLICY.name(), POLICY.riskLevel(), Map.of("orderNo", orderNo), orderNo,
                Map.of("orderId", order.id(), "status", order.status()));
    }

    @Override
    public ToolExecutionOutcome execute(ToolContext context, PreparedToolCall call) {
        OrderResponse order = orderService.findByOrderNo(context.user(), (String) call.arguments().get("orderNo"));
        List<TimelineItem> timeline = order.timeline() == null ? List.of() : order.timeline().stream()
                .limit(5)
                .map(event -> new TimelineItem(event.eventType(), event.title(), event.detail(), event.occurredAt()))
                .toList();
        LogisticsView view = new LogisticsView(
                order.orderNo(),
                order.status(),
                order.shippingCarrier(),
                order.trackingNo(),
                order.shippedAt(),
                timeline);
        return new ToolExecutionOutcome(Map.of("logistics", view), "物流查询成功");
    }

    public record LogisticsView(
            String orderNo,
            String status,
            String carrier,
            String trackingNo,
            Instant shippedAt,
            List<TimelineItem> timeline
    ) {
    }

    public record TimelineItem(
            String eventType,
            String title,
            String detail,
            Instant occurredAt
    ) {
    }
}

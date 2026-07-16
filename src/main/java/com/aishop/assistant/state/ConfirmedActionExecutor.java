package com.aishop.assistant.state;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.tool.ToolExecutionOutcome;
import com.aishop.domain.AppUser;
import com.aishop.service.OrderService;

@Service
public class ConfirmedActionExecutor {

    private final OrderService orderService;

    public ConfirmedActionExecutor(OrderService orderService) {
        this.orderService = orderService;
    }

    public ToolExecutionOutcome execute(AppUser user,
                                        AssistantAction action,
                                        Map<String, Object> arguments) {
        if (action != AssistantAction.CANCEL_ORDER) {
            throw new IllegalArgumentException("当前阶段只支持确认取消订单");
        }
        String orderNo = requiredText(arguments, "orderNo").toUpperCase();
        String note = optionalText(arguments, "note");
        var current = orderService.findByOrderNo(user, orderNo);
        var cancelled = orderService.cancelOrder(user, current.id(), note, "AI 客服");
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("id", cancelled.id());
        order.put("orderNo", cancelled.orderNo());
        order.put("status", cancelled.status());
        order.put("totalAmount", cancelled.totalAmount());
        return new ToolExecutionOutcome(Map.of("order", order), "订单取消成功");
    }

    private String requiredText(Map<String, Object> arguments, String name) {
        String value = optionalText(arguments, name);
        if (value == null) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }

    private String optionalText(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text.strip();
    }
}

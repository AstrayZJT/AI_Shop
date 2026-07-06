package com.aishop.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.domain.OrderStatus;
import com.aishop.domain.OrderTimelineEvent;
import com.aishop.domain.ShopOrder;
import com.aishop.dto.OrderDtos.OrderTimelineResponse;
import com.aishop.repository.OrderTimelineEventRepository;

@Service
public class OrderTimelineService {

    private final OrderTimelineEventRepository orderTimelineEventRepository;

    public OrderTimelineService(OrderTimelineEventRepository orderTimelineEventRepository) {
        this.orderTimelineEventRepository = orderTimelineEventRepository;
    }

    @Transactional
    public List<OrderTimelineResponse> listOrderTimeline(ShopOrder order) {
        ensureTimelineSeeded(order);
        return orderTimelineEventRepository.findByOrderOrderByOccurredAtAscIdAsc(order).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void recordCustomerEvent(ShopOrder order, String eventType, String title, String detail) {
        recordEvent(order, Instant.now(), eventType, title, detail, "用户");
    }

    @Transactional
    public void recordAssistantEvent(ShopOrder order, String eventType, String title, String detail) {
        recordEvent(order, Instant.now(), eventType, title, detail, "AI 客服");
    }

    @Transactional
    public void recordAdminEvent(ShopOrder order, String eventType, String title, String detail, String adminLabel) {
        recordEvent(order, Instant.now(), eventType, title, detail, blankToDefault(adminLabel, "管理员"));
    }

    @Transactional
    public void recordSystemEvent(ShopOrder order, String eventType, String title, String detail, Instant occurredAt) {
        recordEvent(order, occurredAt, eventType, title, detail, "系统");
    }

    @Transactional
    public void ensureTimelineSeeded(ShopOrder order) {
        if (orderTimelineEventRepository.existsByOrder(order)) {
            return;
        }
        persist(order,
                safeInstant(order.getCreatedAt()),
                "ORDER_CREATED",
                "订单已创建",
                buildCreatedDetail(order),
                "系统");
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            persist(order,
                    safeInstant(order.getUpdatedAt(), order.getCreatedAt()),
                    "ORDER_PAYMENT_PENDING",
                    "订单等待支付",
                    "订单已创建，等待用户完成支付。",
                    "系统");
        }
        if (order.getPaidAt() != null) {
            persist(order,
                    order.getPaidAt(),
                    "ORDER_PAID",
                    "订单支付成功",
                    buildPaidDetail(order),
                    "系统");
        }
        if (order.getStatus() == OrderStatus.PROCESSING) {
            persist(order,
                    safeInstant(order.getUpdatedAt(), order.getCreatedAt()),
                    "ORDER_PROCESSING",
                    "订单进入处理中",
                    "平台已开始备货、拣货或等待出库。",
                    "系统");
        }
        if (order.getShippedAt() != null) {
            persist(order,
                    order.getShippedAt(),
                    "ORDER_SHIPPED",
                    "订单已发货",
                    buildShippingDetail(order, "系统补全物流轨迹"),
                    "系统");
        }
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.COMPLETED) {
            persist(order,
                    safeInstant(order.getUpdatedAt(), order.getShippedAt(), order.getCreatedAt()),
                    "ORDER_COMPLETED",
                    "订单已完成",
                    blankToDefault(order.getRiskNote(), "订单已完成收货流程。"),
                    "系统");
        } else if (status == OrderStatus.REFUND_REQUESTED) {
            persist(order,
                    safeInstant(order.getUpdatedAt(), order.getShippedAt(), order.getCreatedAt()),
                    "REFUND_REQUESTED",
                    "退款申请已提交",
                    blankToDefault(order.getRiskNote(), "订单已进入退款审核流程。"),
                    "系统");
        } else if (status == OrderStatus.REFUNDED) {
            persist(order,
                    safeInstant(order.getUpdatedAt(), order.getShippedAt(), order.getCreatedAt()),
                    "REFUND_COMPLETED",
                    "退款已完成",
                    blankToDefault(order.getRiskNote(), "订单退款流程已经处理完成。"),
                    "系统");
        } else if (status == OrderStatus.CANCELLED) {
            persist(order,
                    safeInstant(order.getUpdatedAt(), order.getCreatedAt()),
                    "ORDER_CANCELLED",
                    "订单已取消",
                    blankToDefault(order.getRiskNote(), "订单已结束，不再继续履约。"),
                    "系统");
        }
    }

    private void recordEvent(ShopOrder order,
                             Instant occurredAt,
                             String eventType,
                             String title,
                             String detail,
                             String actorLabel) {
        ensureTimelineSeeded(order);
        persist(order, safeInstant(occurredAt), eventType, title, detail, actorLabel);
    }

    private void persist(ShopOrder order,
                         Instant occurredAt,
                         String eventType,
                         String title,
                         String detail,
                         String actorLabel) {
        OrderTimelineEvent event = new OrderTimelineEvent();
        event.setOrder(order);
        event.setOccurredAt(safeInstant(occurredAt));
        event.setEventType(blankToDefault(eventType, "ORDER_EVENT"));
        event.setTitle(blankToDefault(title, "订单状态更新"));
        event.setDetail(blankToNull(detail));
        event.setActorLabel(blankToDefault(actorLabel, "系统"));
        orderTimelineEventRepository.save(event);
    }

    private OrderTimelineResponse toResponse(OrderTimelineEvent event) {
        return new OrderTimelineResponse(
                event.getEventType(),
                event.getTitle(),
                event.getDetail(),
                event.getActorLabel(),
                event.getOccurredAt());
    }

    private String buildCreatedDetail(ShopOrder order) {
        return "订单金额 %s，收货地址 %s。"
                .formatted(
                        order.getTotalAmount(),
                        blankToDefault(order.getShippingAddress(), "待补充收货地址"));
    }

    private String buildShippingDetail(ShopOrder order, String note) {
        String carrier = blankToDefault(order.getShippingCarrier(), "待补充物流公司");
        String trackingNo = blankToDefault(order.getTrackingNo(), "待补充运单号");
        String suffix = blankToNull(note);
        return suffix == null
                ? "物流公司 %s，运单号 %s。".formatted(carrier, trackingNo)
                : "物流公司 %s，运单号 %s。%s".formatted(carrier, trackingNo, suffix);
    }

    private String buildPaidDetail(ShopOrder order) {
        return "支付方式 %s，支付流水 %s。".formatted(
                blankToDefault(order.getPaymentMethod(), "模拟支付"),
                blankToDefault(order.getPaymentReference(), "待补充"));
    }

    private Instant safeInstant(Instant primary, Instant... fallbacks) {
        if (primary != null) {
            return primary;
        }
        if (fallbacks != null) {
            for (Instant fallback : fallbacks) {
                if (fallback != null) {
                    return fallback;
                }
            }
        }
        return Instant.now();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized == null ? fallback : normalized;
    }
}

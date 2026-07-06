package com.aishop.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.domain.AppUser;
import com.aishop.domain.OrderItem;
import com.aishop.domain.OrderStatus;
import com.aishop.domain.PendingOrderDraft;
import com.aishop.domain.Product;
import com.aishop.domain.ShopOrder;
import com.aishop.dto.OrderDtos.PendingOrderDraftResponse;
import com.aishop.dto.OrderDtos.OrderDraftResponse;
import com.aishop.dto.OrderDtos.OrderItemResponse;
import com.aishop.dto.OrderDtos.OrderResponse;
import com.aishop.repository.OrderItemRepository;
import com.aishop.repository.PendingOrderDraftRepository;
import com.aishop.repository.ShopOrderRepository;

@Service
public class OrderService {

    private static final Pattern DRAFT_PATTERN = Pattern.compile(
            "\\\"productId\\\":(?<productId>\\d+),\\\"productName\\\":\\\"(?<productName>.*?)\\\",\\\"quantity\\\":(?<quantity>\\d+),\\\"unitPrice\\\":(?<unitPrice>[-\\d.]+),\\\"totalAmount\\\":(?<totalAmount>[-\\d.]+),\\\"note\\\":\\\"(?<note>.*?)\\\""
    );

    private final ShopOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PendingOrderDraftRepository draftRepository;
    private final ProductService productService;
    private final OrderTimelineService orderTimelineService;
    private final AfterSalesService afterSalesService;

    public OrderService(ShopOrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        PendingOrderDraftRepository draftRepository,
                        ProductService productService,
                        OrderTimelineService orderTimelineService,
                        AfterSalesService afterSalesService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.draftRepository = draftRepository;
        this.productService = productService;
        this.orderTimelineService = orderTimelineService;
        this.afterSalesService = afterSalesService;
    }

    @Transactional
    public List<OrderResponse> listOrders(AppUser user) {
        return orderRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::toResponse).toList();
    }

    @Transactional
    public OrderResponse detail(AppUser user, Long id) {
        var order = requireOwnedOrder(user, id);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse findByOrderNo(AppUser user, String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return null;
        }
        var order = orderRepository.findByOrderNo(orderNo.trim())
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!order.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("无权访问该订单");
        }
        return toResponse(order);
    }

    @Transactional
    public OrderDraftResponse buildDraft(AppUser user, Long productId, Integer quantity, String threadId) {
        Product product = productService.getProduct(productId);
        int safeQty = quantity == null || quantity <= 0 ? 1 : quantity;
        BigDecimal total = product.getPrice().multiply(BigDecimal.valueOf(safeQty));

        var draft = new PendingOrderDraft();
        draft.setUser(user);
        draft.setThreadId(threadId == null || threadId.isBlank() ? UUID.randomUUID().toString() : threadId);
        draft.setDraftJson("""
                {"productId":%d,"productName":"%s","quantity":%d,"unitPrice":%s,"totalAmount":%s,"note":"待用户确认后创建正式订单"}
                """.formatted(product.getId(), product.getName(), safeQty, product.getPrice().toPlainString(), total.toPlainString()));
        draft.setStatus("PENDING_CONFIRMATION");
        draft = draftRepository.save(draft);
        return new OrderDraftResponse(draft.getThreadId(), draft.getDraftJson(), draft.getStatus());
    }

    @Transactional(readOnly = true)
    public PendingOrderDraftResponse latestDraft(AppUser user, String threadId) {
        if (threadId == null || threadId.isBlank()) {
            return null;
        }
        return draftRepository.findTop1ByThreadIdAndUserIdOrderByCreatedAtDesc(threadId.trim(), user.getId())
                .filter(draft -> "PENDING_CONFIRMATION".equalsIgnoreCase(draft.getStatus()))
                .map(this::toDraftResponse)
                .orElse(null);
    }

    @Transactional
    public OrderResponse confirmDraft(AppUser user, String threadId) {
        var draft = draftRepository.findTop1ByThreadIdOrderByCreatedAtDesc(threadId)
                .orElseThrow(() -> new IllegalArgumentException("未找到订单草稿"));
        if (!draft.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("无权确认该草稿");
        }
        if (!"PENDING_CONFIRMATION".equalsIgnoreCase(draft.getStatus())) {
            throw new IllegalArgumentException("当前草稿已失效，请重新生成");
        }

        DraftPayload payload = parseDraft(draft.getDraftJson());
        Product product = productService.getProduct(payload.productId());
        productService.decreaseStock(product, payload.quantity());

        var order = new ShopOrder();
        order.setOrderNo("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setUser(user);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setTotalAmount(payload.totalAmount());
        order.setShippingAddress(user.getShippingAddress() == null ? "待补充收货地址" : user.getShippingAddress());
        order.setRiskNote("已由用户确认");
        order = orderRepository.save(order);

        var item = new OrderItem();
        item.setOrder(order);
        item.setProductName(payload.productName());
        item.setProductSku(product.getSku());
        item.setQuantity(payload.quantity());
        item.setUnitPrice(payload.unitPrice());
        item.setLineTotal(payload.totalAmount());
        orderItemRepository.save(item);

        draft.setStatus("CONFIRMED");
        draftRepository.save(draft);
        orderTimelineService.recordCustomerEvent(
                order,
                "ORDER_CREATED",
                "用户确认 AI 下单草稿",
                "已确认 %s x %s，并生成正式订单。".formatted(payload.productName(), payload.quantity()));
        return toResponse(order);
    }

    @Transactional
    public PendingOrderDraftResponse cancelDraft(AppUser user, String threadId) {
        if (threadId == null || threadId.isBlank()) {
            throw new IllegalArgumentException("缺少草稿标识");
        }
        var draft = draftRepository.findTop1ByThreadIdAndUserIdOrderByCreatedAtDesc(threadId.trim(), user.getId())
                .orElseThrow(() -> new IllegalArgumentException("未找到订单草稿"));
        if (!"PENDING_CONFIRMATION".equalsIgnoreCase(draft.getStatus())) {
            throw new IllegalArgumentException("当前草稿已无法取消");
        }
        draft.setStatus("CANCELLED");
        draftRepository.save(draft);
        return toDraftResponse(draft);
    }

    @Transactional(readOnly = true)
    public ShopOrder getOrderEntity(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
    }

    @Transactional
    public List<OrderResponse> listAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public OrderResponse updateStatus(Long id, String rawStatus) {
        ShopOrder order = getOrderEntity(id);
        OrderStatus previousStatus = order.getStatus();
        try {
            order.setStatus(OrderStatus.valueOf(rawStatus));
        } catch (Exception ex) {
            throw new IllegalArgumentException("不支持的订单状态: " + rawStatus);
        }
        if (order.getStatus() == OrderStatus.SHIPPED && order.getShippedAt() == null) {
            order.setShippedAt(Instant.now());
        }
        orderRepository.save(order);
        orderTimelineService.recordSystemEvent(
                order,
                "ORDER_STATUS_CHANGED",
                "订单状态已更新为" + statusLabel(order.getStatus()),
                "系统将订单状态从 %s 调整为 %s。".formatted(statusLabel(previousStatus), statusLabel(order.getStatus())),
                Instant.now());
        return toResponse(order);
    }

    @Transactional
    public OrderResponse updateShippingAddress(AppUser user, Long id, String shippingAddress, String note) {
        return updateShippingAddress(user, id, shippingAddress, note, "用户");
    }

    @Transactional
    public OrderResponse updateShippingAddress(AppUser user, Long id, String shippingAddress, String note, String actorLabel) {
        ShopOrder order = requireOwnedOrder(user, id);
        if (!canUpdateShippingAddress(order)) {
            throw new IllegalArgumentException("当前订单状态暂不支持修改收货地址");
        }
        String normalizedAddress = shippingAddress == null ? "" : shippingAddress.trim();
        if (normalizedAddress.isBlank()) {
            throw new IllegalArgumentException("请先填写新的收货地址");
        }
        order.setShippingAddress(normalizedAddress);
        order.setRiskNote(appendRiskNote(order.getRiskNote(), "用户修改收货地址", note));
        orderRepository.save(order);
        recordTimelineByActor(
                order,
                actorLabel,
                "ORDER_ADDRESS_UPDATED",
                "更新收货地址",
                "收货地址已改为 %s。%s".formatted(
                        normalizedAddress,
                        composeOptionalSuffix(note)));
        return toResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(AppUser user, Long id, String note) {
        return cancelOrder(user, id, note, "用户");
    }

    @Transactional
    public OrderResponse cancelOrder(AppUser user, Long id, String note, String actorLabel) {
        ShopOrder order = requireOwnedOrder(user, id);
        if (!(order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.PROCESSING)) {
            throw new IllegalArgumentException("当前订单状态不支持取消");
        }
        restoreStockForOrder(order);
        order.setStatus(OrderStatus.CANCELLED);
        order.setRiskNote(appendRiskNote(order.getRiskNote(), "用户取消订单", note));
        orderRepository.save(order);
        recordTimelineByActor(
                order,
                actorLabel,
                "ORDER_CANCELLED",
                "订单已取消",
                "订单已取消并回补库存。%s".formatted(composeOptionalSuffix(note)));
        return toResponse(order);
    }

    @Transactional
    public OrderResponse confirmReceipt(AppUser user, Long id) {
        return confirmReceipt(user, id, "用户");
    }

    @Transactional
    public OrderResponse confirmReceipt(AppUser user, Long id, String actorLabel) {
        ShopOrder order = requireOwnedOrder(user, id);
        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new IllegalArgumentException("只有已发货订单才能确认收货");
        }
        order.setStatus(OrderStatus.COMPLETED);
        order.setRiskNote(appendRiskNote(order.getRiskNote(), "用户确认收货", null));
        orderRepository.save(order);
        recordTimelineByActor(
                order,
                actorLabel,
                "ORDER_COMPLETED",
                "订单已完成",
                "订单已确认收货，履约流程完成。");
        return toResponse(order);
    }

    @Transactional
    public OrderResponse requestRefund(AppUser user, Long id, String note) {
        return requestRefund(user, id, note, "用户");
    }

    @Transactional
    public OrderResponse requestRefund(AppUser user, Long id, String note, String actorLabel) {
        ShopOrder order = requireOwnedOrder(user, id);
        if (!(order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.COMPLETED)) {
            throw new IllegalArgumentException("当前订单状态不支持申请退款");
        }
        order.setStatus(OrderStatus.REFUND_REQUESTED);
        order.setRiskNote(appendRiskNote(order.getRiskNote(), "用户发起退款申请", note));
        orderRepository.save(order);
        afterSalesService.submitRefundRequest(order, note);
        recordTimelineByActor(
                order,
                actorLabel,
                "REFUND_REQUESTED",
                "退款申请已提交",
                "订单已进入退款审核流程。%s".formatted(composeOptionalSuffix(note)));
        return toResponse(order);
    }

    @Transactional
    public OrderResponse submitReturnShipment(AppUser user, Long id, String carrier, String trackingNo, String note) {
        ShopOrder order = requireOwnedOrder(user, id);
        if (order.getStatus() != OrderStatus.REFUND_REQUESTED) {
            throw new IllegalArgumentException("当前订单不在售后回寄阶段");
        }
        afterSalesService.submitReturnShipment(order, carrier, trackingNo, note);
        orderTimelineService.recordCustomerEvent(
                order,
                "RETURN_SHIPPED",
                "用户已回寄商品",
                "回寄物流 %s，运单号 %s。%s".formatted(
                        carrier == null ? "" : carrier.trim(),
                        trackingNo == null ? "" : trackingNo.trim(),
                        composeOptionalSuffix(note)));
        return toResponse(order);
    }

    private DraftPayload parseDraft(String draftJson) {
        var matcher = DRAFT_PATTERN.matcher(draftJson == null ? "" : draftJson);
        if (!matcher.find()) {
            throw new IllegalArgumentException("订单草稿格式无效");
        }
        Long productId = Long.valueOf(matcher.group("productId"));
        String productName = matcher.group("productName");
        Integer quantity = Integer.valueOf(matcher.group("quantity"));
        BigDecimal unitPrice = new BigDecimal(matcher.group("unitPrice"));
        BigDecimal totalAmount = new BigDecimal(matcher.group("totalAmount"));
        String note = matcher.group("note");
        return new DraftPayload(productId, productName, quantity, unitPrice, totalAmount, note);
    }

    public OrderResponse toResponse(ShopOrder order) {
        var items = orderItemRepository.findByOrder(order).stream()
                .map(item -> new OrderItemResponse(item.getProductName(), item.getQuantity(), item.getUnitPrice(), item.getLineTotal()))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getShippingAddress(),
                order.getShippingCarrier(),
                order.getTrackingNo(),
                order.getShippedAt(),
                order.getRiskNote(),
                items,
                orderTimelineService.listOrderTimeline(order),
                afterSalesService.toResponse(order));
    }

    private ShopOrder requireOwnedOrder(AppUser user, Long id) {
        var order = orderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!order.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("无权访问该订单");
        }
        return order;
    }

    private String appendRiskNote(String current, String action, String note) {
        String suffix = note == null || note.isBlank() ? action : action + "：" + note.trim();
        if (current == null || current.isBlank()) {
            return suffix;
        }
        return current + " | " + suffix;
    }

    private void recordTimelineByActor(ShopOrder order,
                                       String actorLabel,
                                       String eventType,
                                       String title,
                                       String detail) {
        if ("AI 客服".equals(actorLabel)) {
            orderTimelineService.recordAssistantEvent(order, eventType, title, detail);
            return;
        }
        orderTimelineService.recordCustomerEvent(order, eventType, title, detail);
    }

    private String composeOptionalSuffix(String note) {
        if (note == null || note.isBlank()) {
            return "未补充额外说明。";
        }
        return "备注：" + note.trim();
    }

    private String statusLabel(OrderStatus status) {
        return switch (status) {
            case DRAFT -> "草稿";
            case PENDING_CONFIRMATION -> "待确认";
            case PENDING_PAYMENT -> "待支付";
            case CONFIRMED -> "待发货";
            case PROCESSING -> "处理中";
            case SHIPPED -> "已发货";
            case COMPLETED -> "已完成";
            case REFUND_REQUESTED -> "退款处理中";
            case REFUNDED -> "已退款";
            case CANCELLED -> "已取消";
        };
    }

    void restoreStockForOrder(ShopOrder order) {
        for (OrderItem item : orderItemRepository.findByOrder(order)) {
            productService.increaseStockBySku(item.getProductSku(), item.getQuantity());
        }
    }

    private boolean canUpdateShippingAddress(ShopOrder order) {
        return order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.PROCESSING;
    }

    private PendingOrderDraftResponse toDraftResponse(PendingOrderDraft draft) {
        DraftPayload payload = parseDraft(draft.getDraftJson());
        return new PendingOrderDraftResponse(
                draft.getThreadId(),
                draft.getStatus(),
                payload.productId(),
                payload.productName(),
                payload.quantity(),
                payload.unitPrice(),
                payload.totalAmount(),
                payload.note());
    }

    private record DraftPayload(Long productId,
                                String productName,
                                Integer quantity,
                                BigDecimal unitPrice,
                                BigDecimal totalAmount,
                                String note) {}
}

package com.aishop.service;

import java.math.BigDecimal;
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
import com.aishop.dto.OrderDtos.OrderDraftResponse;
import com.aishop.dto.OrderDtos.OrderItemResponse;
import com.aishop.dto.OrderDtos.OrderResponse;
import com.aishop.repository.OrderItemRepository;
import com.aishop.repository.PendingOrderDraftRepository;
import com.aishop.repository.ShopOrderRepository;

@Service
public class OrderService {

    private static final Pattern DRAFT_PATTERN = Pattern.compile(
            "\\\"productId\\\":(?<productId>\\d+),\\\"productName\\\":\\\"(?<productName>.*?)\\\",\\\"quantity\\\":(?<quantity>\\d+),\\\"unitPrice\\\":(?<unitPrice>[-\\d.]+),\\\"totalAmount\\\":(?<totalAmount>[-\\d.]+)"
    );

    private final ShopOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PendingOrderDraftRepository draftRepository;
    private final ProductService productService;

    public OrderService(ShopOrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        PendingOrderDraftRepository draftRepository,
                        ProductService productService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.draftRepository = draftRepository;
        this.productService = productService;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders(AppUser user) {
        return orderRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse detail(AppUser user, Long id) {
        var order = orderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
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

    @Transactional
    public ShopOrder confirmDraft(AppUser user, String threadId) {
        var draft = draftRepository.findTop1ByThreadIdOrderByCreatedAtDesc(threadId)
                .orElseThrow(() -> new IllegalArgumentException("未找到订单草稿"));
        if (!draft.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("无权确认该草稿");
        }

        DraftPayload payload = parseDraft(draft.getDraftJson());
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
        item.setQuantity(payload.quantity());
        item.setUnitPrice(payload.unitPrice());
        item.setLineTotal(payload.totalAmount());
        orderItemRepository.save(item);

        draft.setStatus("CONFIRMED");
        draftRepository.save(draft);
        return order;
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
        return new DraftPayload(productId, productName, quantity, unitPrice, totalAmount);
    }

    private OrderResponse toResponse(ShopOrder order) {
        var items = orderItemRepository.findByOrder(order).stream()
                .map(item -> new OrderItemResponse(item.getProductName(), item.getQuantity(), item.getUnitPrice(), item.getLineTotal()))
                .toList();
        return new OrderResponse(order.getId(), order.getOrderNo(), order.getStatus().name(), order.getTotalAmount(), order.getShippingAddress(), order.getRiskNote(), items);
    }

    private record DraftPayload(Long productId, String productName, Integer quantity, BigDecimal unitPrice, BigDecimal totalAmount) {}
}

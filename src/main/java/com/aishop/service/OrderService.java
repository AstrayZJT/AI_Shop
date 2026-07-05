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
        var order = requireOwnedOrder(user, id);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public List<OrderResponse> listAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public OrderResponse updateStatus(Long id, String rawStatus) {
        ShopOrder order = getOrderEntity(id);
        try {
            order.setStatus(OrderStatus.valueOf(rawStatus));
        } catch (Exception ex) {
            throw new IllegalArgumentException("不支持的订单状态: " + rawStatus);
        }
        orderRepository.save(order);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(AppUser user, Long id, String note) {
        ShopOrder order = requireOwnedOrder(user, id);
        if (!(order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.PROCESSING)) {
            throw new IllegalArgumentException("当前订单状态不支持取消");
        }
        restoreStockForOrder(order);
        order.setStatus(OrderStatus.CANCELLED);
        order.setRiskNote(appendRiskNote(order.getRiskNote(), "用户取消订单", note));
        orderRepository.save(order);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse confirmReceipt(AppUser user, Long id) {
        ShopOrder order = requireOwnedOrder(user, id);
        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new IllegalArgumentException("只有已发货订单才能确认收货");
        }
        order.setStatus(OrderStatus.COMPLETED);
        order.setRiskNote(appendRiskNote(order.getRiskNote(), "用户确认收货", null));
        orderRepository.save(order);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse requestRefund(AppUser user, Long id, String note) {
        ShopOrder order = requireOwnedOrder(user, id);
        if (!(order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.COMPLETED)) {
            throw new IllegalArgumentException("当前订单状态不支持申请退款");
        }
        order.setStatus(OrderStatus.REFUND_REQUESTED);
        order.setRiskNote(appendRiskNote(order.getRiskNote(), "用户发起退款申请", note));
        orderRepository.save(order);
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
        return new OrderResponse(order.getId(), order.getOrderNo(), order.getStatus().name(), order.getTotalAmount(), order.getShippingAddress(), order.getRiskNote(), items);
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

    void restoreStockForOrder(ShopOrder order) {
        for (OrderItem item : orderItemRepository.findByOrder(order)) {
            productService.increaseStockBySku(item.getProductSku(), item.getQuantity());
        }
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

package com.aishop.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.domain.AppUser;
import com.aishop.domain.AssistantMessage;
import com.aishop.domain.AssistantSession;
import com.aishop.domain.KnowledgeDocument;
import com.aishop.domain.OrderStatus;
import com.aishop.domain.PendingOrderDraft;
import com.aishop.domain.Product;
import com.aishop.domain.ShopOrder;
import com.aishop.dto.AdminDtos.AdminAssistantDraftResponse;
import com.aishop.dto.AdminDtos.AdminAssistantMessageResponse;
import com.aishop.dto.AdminDtos.AdminAssistantSessionResponse;
import com.aishop.dto.AdminDtos.AdminOrderItemResponse;
import com.aishop.dto.AdminDtos.AdminOrderResponse;
import com.aishop.dto.AdminDtos.AdminUserResponse;
import com.aishop.dto.AdminDtos.DashboardMetricResponse;
import com.aishop.dto.AdminDtos.KnowledgeDocumentResponse;
import com.aishop.dto.AdminDtos.KnowledgeSearchResponse;
import com.aishop.dto.AdminDtos.ProductUpsertRequest;
import com.aishop.dto.ProductDtos.ProductResponse;
import com.aishop.repository.AppUserRepository;
import com.aishop.repository.AssistantMessageRepository;
import com.aishop.repository.AssistantSessionRepository;
import com.aishop.repository.KnowledgeDocumentRepository;
import com.aishop.repository.OrderItemRepository;
import com.aishop.repository.PendingOrderDraftRepository;
import com.aishop.repository.ProductRepository;
import com.aishop.repository.ShopOrderRepository;

@Service
public class AdminService {

    private static final String SERVICE_STATUS_ACTIVE = "ACTIVE";
    private static final String SERVICE_STATUS_ESCALATED = "ESCALATED";
    private static final String SERVICE_STATUS_RESOLVED = "RESOLVED";
    private static final Pattern DRAFT_PATTERN = Pattern.compile(
            "\\\"productId\\\":(?<productId>\\d+),\\\"productName\\\":\\\"(?<productName>.*?)\\\",\\\"quantity\\\":(?<quantity>\\d+),\\\"unitPrice\\\":(?<unitPrice>[-\\d.]+),\\\"totalAmount\\\":(?<totalAmount>[-\\d.]+),\\\"note\\\":\\\"(?<note>.*?)\\\""
    );

    private final AppUserRepository userRepository;
    private final AssistantSessionRepository assistantSessionRepository;
    private final AssistantMessageRepository assistantMessageRepository;
    private final ProductRepository productRepository;
    private final ShopOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PendingOrderDraftRepository pendingOrderDraftRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final ProductService productService;
    private final KnowledgeService knowledgeService;

    public AdminService(AppUserRepository userRepository,
                        AssistantSessionRepository assistantSessionRepository,
                        AssistantMessageRepository assistantMessageRepository,
                        ProductRepository productRepository,
                        ShopOrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        PendingOrderDraftRepository pendingOrderDraftRepository,
                        KnowledgeDocumentRepository knowledgeDocumentRepository,
                        ProductService productService,
                        KnowledgeService knowledgeService) {
        this.userRepository = userRepository;
        this.assistantSessionRepository = assistantSessionRepository;
        this.assistantMessageRepository = assistantMessageRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.pendingOrderDraftRepository = pendingOrderDraftRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.productService = productService;
        this.knowledgeService = knowledgeService;
    }

    @Transactional(readOnly = true)
    public DashboardMetricResponse dashboard() {
        List<ShopOrder> orders = orderRepository.findAllByOrderByCreatedAtDesc();
        BigDecimal totalRevenue = orders.stream()
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.REFUNDED)
                .map(ShopOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long pendingShipmentCount = orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.PROCESSING)
                .count();
        long lowStockCount = productRepository.findByStockLessThanEqualOrderByStockAsc(5).size();
        return new DashboardMetricResponse(
                userRepository.count(),
                productRepository.count(),
                orderRepository.count(),
                totalRevenue,
                knowledgeDocumentRepository.count(),
                lowStockCount,
                pendingShipmentCount);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listProducts() {
        return productService.listAll();
    }

    @Transactional
    public ProductResponse createProduct(ProductUpsertRequest request) {
        ensureUniqueSku(request.sku(), null);
        Product product = new Product();
        applyProductFields(product, request);
        return productService.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductUpsertRequest request) {
        Product product = productRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("商品不存在"));
        ensureUniqueSku(request.sku(), id);
        applyProductFields(product, request);
        return productService.toResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public List<AdminOrderResponse> listOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toAdminOrderResponse).toList();
    }

    @Transactional
    public AdminOrderResponse updateOrderStatus(Long id, String rawStatus, String note, String shippingCarrier, String trackingNo) {
        ShopOrder order = orderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        OrderStatus nextStatus = parseStatus(rawStatus);
        if (nextStatus == OrderStatus.REFUNDED || order.getStatus() == OrderStatus.REFUND_REQUESTED) {
            throw new IllegalArgumentException("退款申请请使用售后审核动作处理");
        }
        validateStatusTransition(order.getStatus(), nextStatus);
        if (nextStatus == OrderStatus.CANCELLED && order.getStatus() != OrderStatus.CANCELLED) {
            restoreStockForOrder(order);
        }
        if (nextStatus == OrderStatus.SHIPPED) {
            applyShipmentDetails(order, shippingCarrier, trackingNo);
        }
        order.setStatus(nextStatus);
        order.setRiskNote(appendRiskNote(order.getRiskNote(), "管理员更新状态为" + nextStatus.name(), note));
        orderRepository.save(order);
        return toAdminOrderResponse(order);
    }

    @Transactional
    public AdminOrderResponse reviewRefund(Long id, Boolean approved, String note) {
        ShopOrder order = orderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (order.getStatus() != OrderStatus.REFUND_REQUESTED) {
            throw new IllegalArgumentException("当前订单不在退款审核状态");
        }
        boolean safeApproved = Boolean.TRUE.equals(approved);
        if (safeApproved) {
            restoreStockForOrder(order);
            order.setStatus(OrderStatus.REFUNDED);
            order.setRiskNote(appendRiskNote(order.getRiskNote(), "管理员同意退款", note));
        } else {
            order.setStatus(OrderStatus.COMPLETED);
            order.setRiskNote(appendRiskNote(order.getRiskNote(), "管理员驳回退款", note));
        }
        orderRepository.save(order);
        return toAdminOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocumentResponse> listKnowledgeDocuments() {
        return knowledgeDocumentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toKnowledgeDocumentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public KnowledgeSearchResponse searchKnowledge(String keyword) {
        return new KnowledgeSearchResponse(keyword, knowledgeService.search(keyword));
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toAdminUserResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminAssistantSessionResponse> listAssistantSessions() {
        return assistantSessionRepository.findAll().stream()
                .sorted(assistantSessionComparator())
                .limit(20)
                .map(this::toAdminAssistantSessionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminAssistantSessionResponse> listEscalatedAssistantSessions() {
        return assistantSessionRepository.findAll().stream()
                .filter(session -> SERVICE_STATUS_ESCALATED.equals(normalizeServiceStatus(session.getServiceStatus())))
                .sorted(escalationQueueComparator())
                .limit(20)
                .map(this::toAdminAssistantSessionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminAssistantMessageResponse> assistantMessages(Long sessionId) {
        AssistantSession session = assistantSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("AI 会话不存在"));
        return assistantMessageRepository.findBySessionOrderByCreatedAtAsc(session).stream()
                .map(message -> new AdminAssistantMessageResponse(
                        message.getRole(),
                        message.getContent(),
                        message.getCreatedAt()))
                .toList();
    }

    @Transactional
    public AdminAssistantSessionResponse claimAssistantSession(Long sessionId, AppUser admin) {
        AssistantSession session = assistantSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("AI 会话不存在"));
        if (!SERVICE_STATUS_ESCALATED.equals(normalizeServiceStatus(session.getServiceStatus()))) {
            throw new IllegalArgumentException("当前会话未处于人工跟进状态");
        }

        assignSession(session, admin, Instant.now());
        assistantSessionRepository.save(session);
        return toAdminAssistantSessionResponse(session);
    }

    @Transactional
    public AdminAssistantSessionResponse assignAssistantSession(Long sessionId, AppUser actingAdmin, String adminUsername) {
        AssistantSession session = assistantSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("AI 会话不存在"));
        if (!SERVICE_STATUS_ESCALATED.equals(normalizeServiceStatus(session.getServiceStatus()))) {
            throw new IllegalArgumentException("只有人工跟进中的会话支持转交");
        }
        String normalizedUsername = blankToNull(adminUsername);
        if (normalizedUsername == null) {
            throw new IllegalArgumentException("请选择要转交的客服账号");
        }

        AppUser targetAdmin = userRepository.findByUsername(normalizedUsername)
                .filter(user -> user.getRole() == com.aishop.domain.UserRole.ADMIN)
                .orElseThrow(() -> new IllegalArgumentException("目标客服账号不存在或不是管理员"));

        assignSession(session, targetAdmin, Instant.now());
        session.setSummary(appendAssistantSummary(
                session.getSummary(),
                "系统",
                "由 %s 转交给 %s".formatted(actingAdmin.getDisplayName(), targetAdmin.getDisplayName())));
        assistantSessionRepository.save(session);
        return toAdminAssistantSessionResponse(session);
    }

    @Transactional(readOnly = true)
    public List<AdminAssistantDraftResponse> listPendingAssistantDrafts() {
        return pendingOrderDraftRepository.findTop20ByStatusOrderByCreatedAtDesc("PENDING_CONFIRMATION").stream()
                .map(this::toAdminAssistantDraftResponse)
                .toList();
    }

    @Transactional
    public AdminAssistantMessageResponse replyAssistantSession(Long sessionId, AppUser admin, String content, Boolean resolve) {
        AssistantSession session = assistantSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("AI 会话不存在"));
        String normalizedContent = blankToNull(content);
        if (normalizedContent == null) {
            throw new IllegalArgumentException("人工回复内容不能为空");
        }
        Instant now = Instant.now();

        assignSession(session, admin, now);

        AssistantMessage message = new AssistantMessage();
        message.setSession(session);
        message.setRole("support");
        message.setContent(normalizedContent);
        AssistantMessage saved = assistantMessageRepository.save(message);
        Instant messageTime = saved.getCreatedAt() == null ? now : saved.getCreatedAt();

        session.setServiceStatus(Boolean.TRUE.equals(resolve) ? SERVICE_STATUS_RESOLVED : SERVICE_STATUS_ESCALATED);
        session.setLastIntent("handoff");
        session.setLastSupportMessageAt(messageTime);
        session.setSupportUnreadCount(0L);
        session.setCustomerUnreadCount(safeCount(session.getCustomerUnreadCount()) + 1L);
        if (session.getFirstSupportReplyAt() == null) {
            session.setFirstSupportReplyAt(messageTime);
        }
        session.setResolvedAt(Boolean.TRUE.equals(resolve) ? messageTime : null);
        session.setSummary(appendAssistantSummary(session.getSummary(), "人工客服", normalizedContent));
        assistantSessionRepository.save(session);

        return new AdminAssistantMessageResponse(saved.getRole(), saved.getContent(), saved.getCreatedAt());
    }

    private void ensureUniqueSku(String sku, Long currentId) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU 不能为空");
        }
        productRepository.findBySku(sku.trim()).ifPresent(product -> {
            if (currentId == null || !product.getId().equals(currentId)) {
                throw new IllegalArgumentException("SKU 已存在");
            }
        });
    }

    private void applyProductFields(Product product, ProductUpsertRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("商品名称不能为空");
        }
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("商品价格不合法");
        }
        if (request.stock() == null || request.stock() < 0) {
            throw new IllegalArgumentException("商品库存不合法");
        }
        product.setSku(request.sku().trim());
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setImageUrl(request.imageUrl());
        product.setCategory(productService.getOrCreateCategory(request.categoryName(), request.categoryDescription()));
    }

    private AdminOrderResponse toAdminOrderResponse(ShopOrder order) {
        List<AdminOrderItemResponse> items = orderItemRepository.findByOrder(order).stream()
                .map(item -> new AdminOrderItemResponse(
                        item.getProductName(),
                        item.getProductSku(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getLineTotal()))
                .toList();
        AppUser user = order.getUser();
        return new AdminOrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getStatus().name(),
                user.getUsername(),
                user.getDisplayName(),
                order.getTotalAmount(),
                order.getShippingAddress(),
                order.getShippingCarrier(),
                order.getTrackingNo(),
                order.getShippedAt(),
                order.getRiskNote(),
                order.getCreatedAt(),
                items);
    }

    private KnowledgeDocumentResponse toKnowledgeDocumentResponse(KnowledgeDocument document) {
        String content = document.getContent() == null ? "" : document.getContent().trim();
        String preview = content.length() <= 120 ? content : content.substring(0, 120) + "...";
        return new KnowledgeDocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getDocType(),
                preview,
                document.getCreatedAt());
    }

    private AdminUserResponse toAdminUserResponse(AppUser user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole().name(),
                user.getPhone(),
                user.getShippingAddress(),
                user.getCreatedAt());
    }

    private AdminAssistantSessionResponse toAdminAssistantSessionResponse(AssistantSession session) {
        AppUser user = session.getUser();
        AppUser assignedAdmin = session.getAssignedAdmin();
        return new AdminAssistantSessionResponse(
                session.getId(),
                "assistant-" + session.getId(),
                session.getTitle(),
                session.getSummary(),
                session.getLastIntent(),
                normalizeServiceStatus(session.getServiceStatus()),
                user.getUsername(),
                user.getDisplayName(),
                assistantMessageRepository.countBySession(session),
                assignedAdmin == null ? null : assignedAdmin.getUsername(),
                assignedAdmin == null ? null : assignedAdmin.getDisplayName(),
                session.getAssignedAt(),
                session.getFirstSupportReplyAt(),
                session.getResolvedAt(),
                session.getLastCustomerMessageAt(),
                session.getLastSupportMessageAt(),
                safeCount(session.getSupportUnreadCount()),
                safeCount(session.getCustomerUnreadCount()),
                session.getCreatedAt());
    }

    private AdminAssistantDraftResponse toAdminAssistantDraftResponse(PendingOrderDraft draft) {
        DraftPayload payload = parseDraft(draft.getDraftJson());
        AppUser user = draft.getUser();
        return new AdminAssistantDraftResponse(
                draft.getId(),
                draft.getThreadId(),
                draft.getStatus(),
                user.getUsername(),
                user.getDisplayName(),
                payload.productId(),
                payload.productName(),
                payload.quantity(),
                payload.unitPrice(),
                payload.totalAmount(),
                payload.note(),
                draft.getCreatedAt());
    }

    private OrderStatus parseStatus(String rawStatus) {
        try {
            return OrderStatus.valueOf(rawStatus);
        } catch (Exception ex) {
            throw new IllegalArgumentException("不支持的订单状态: " + rawStatus);
        }
    }

    private void validateStatusTransition(OrderStatus currentStatus, OrderStatus nextStatus) {
        if (currentStatus == nextStatus) {
            return;
        }
        boolean allowed = switch (currentStatus) {
            case DRAFT -> nextStatus == OrderStatus.PENDING_CONFIRMATION
                    || nextStatus == OrderStatus.CONFIRMED
                    || nextStatus == OrderStatus.CANCELLED;
            case PENDING_CONFIRMATION -> nextStatus == OrderStatus.CONFIRMED
                    || nextStatus == OrderStatus.CANCELLED;
            case CONFIRMED -> nextStatus == OrderStatus.PROCESSING
                    || nextStatus == OrderStatus.SHIPPED
                    || nextStatus == OrderStatus.CANCELLED;
            case PROCESSING -> nextStatus == OrderStatus.SHIPPED
                    || nextStatus == OrderStatus.CANCELLED;
            case SHIPPED -> nextStatus == OrderStatus.COMPLETED;
            case COMPLETED, REFUND_REQUESTED, REFUNDED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new IllegalArgumentException("当前订单状态不支持变更为: " + nextStatus.name());
        }
    }

    private String appendRiskNote(String current, String action, String note) {
        String suffix = note == null || note.isBlank() ? action : action + "：" + note.trim();
        if (current == null || current.isBlank()) {
            return suffix;
        }
        return current + " | " + suffix;
    }

    private String appendAssistantSummary(String current, String speaker, String content) {
        String snippet = (speaker == null ? "" : speaker + "：") + trim(content, 80);
        if (current == null || current.isBlank()) {
            return snippet;
        }
        return current + " | " + snippet;
    }

    private void assignSession(AssistantSession session, AppUser admin, Instant assignTime) {
        if (admin == null || admin.getRole() != com.aishop.domain.UserRole.ADMIN) {
            throw new SecurityException("需要管理员权限");
        }
        boolean changedOwner = session.getAssignedAdmin() == null
                || !session.getAssignedAdmin().getId().equals(admin.getId());
        session.setAssignedAdmin(admin);
        if (changedOwner || session.getAssignedAt() == null) {
            session.setAssignedAt(assignTime);
        }
    }

    private DraftPayload parseDraft(String draftJson) {
        var matcher = DRAFT_PATTERN.matcher(draftJson == null ? "" : draftJson);
        if (!matcher.find()) {
            throw new IllegalArgumentException("AI 下单草稿格式无效");
        }
        return new DraftPayload(
                Long.valueOf(matcher.group("productId")),
                matcher.group("productName"),
                Integer.valueOf(matcher.group("quantity")),
                new BigDecimal(matcher.group("unitPrice")),
                new BigDecimal(matcher.group("totalAmount")),
                matcher.group("note"));
    }

    private void applyShipmentDetails(ShopOrder order, String shippingCarrier, String trackingNo) {
        String normalizedCarrier = blankToNull(shippingCarrier);
        String normalizedTrackingNo = blankToNull(trackingNo);
        if (normalizedCarrier == null || normalizedTrackingNo == null) {
            throw new IllegalArgumentException("标记发货时请填写物流公司和运单号");
        }
        order.setShippingCarrier(normalizedCarrier);
        order.setTrackingNo(normalizedTrackingNo);
        order.setShippedAt(Instant.now());
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeServiceStatus(String status) {
        if (status == null || status.isBlank()) {
            return SERVICE_STATUS_ACTIVE;
        }
        return status.trim().toUpperCase();
    }

    private String trim(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    private void restoreStockForOrder(ShopOrder order) {
        for (var item : orderItemRepository.findByOrder(order)) {
            productService.increaseStockBySku(item.getProductSku(), item.getQuantity());
        }
    }

    private Comparator<AssistantSession> assistantSessionComparator() {
        return Comparator
                .comparing((AssistantSession session) -> !SERVICE_STATUS_ESCALATED.equals(normalizeServiceStatus(session.getServiceStatus())))
                .thenComparing((AssistantSession session) -> safeCount(session.getSupportUnreadCount()), Comparator.reverseOrder())
                .thenComparing((AssistantSession session) -> safeCount(session.getCustomerUnreadCount()), Comparator.reverseOrder())
                .thenComparing(this::latestAssistantActivityAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AssistantSession::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private Comparator<AssistantSession> escalationQueueComparator() {
        return Comparator
                .comparing((AssistantSession session) -> session.getAssignedAdmin() != null)
                .thenComparing((AssistantSession session) -> safeCount(session.getSupportUnreadCount()), Comparator.reverseOrder())
                .thenComparing(this::latestCustomerWaitingAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AssistantSession::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private Instant latestAssistantActivityAt(AssistantSession session) {
        if (session.getUpdatedAt() != null) {
            return session.getUpdatedAt();
        }
        if (session.getLastCustomerMessageAt() != null) {
            return session.getLastCustomerMessageAt();
        }
        if (session.getLastSupportMessageAt() != null) {
            return session.getLastSupportMessageAt();
        }
        return session.getCreatedAt();
    }

    private Instant latestCustomerWaitingAt(AssistantSession session) {
        if (session.getLastCustomerMessageAt() != null) {
            return session.getLastCustomerMessageAt();
        }
        return session.getCreatedAt();
    }

    private long safeCount(Long count) {
        return count == null ? 0L : count;
    }

    private record DraftPayload(Long productId,
                                String productName,
                                Integer quantity,
                                BigDecimal unitPrice,
                                BigDecimal totalAmount,
                                String note) {}
}

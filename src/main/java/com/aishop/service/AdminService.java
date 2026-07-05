package com.aishop.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.domain.AppUser;
import com.aishop.domain.KnowledgeDocument;
import com.aishop.domain.OrderStatus;
import com.aishop.domain.Product;
import com.aishop.domain.ShopOrder;
import com.aishop.dto.AdminDtos.AdminOrderItemResponse;
import com.aishop.dto.AdminDtos.AdminOrderResponse;
import com.aishop.dto.AdminDtos.AdminUserResponse;
import com.aishop.dto.AdminDtos.DashboardMetricResponse;
import com.aishop.dto.AdminDtos.KnowledgeDocumentResponse;
import com.aishop.dto.AdminDtos.KnowledgeSearchResponse;
import com.aishop.dto.AdminDtos.ProductUpsertRequest;
import com.aishop.dto.ProductDtos.ProductResponse;
import com.aishop.repository.AppUserRepository;
import com.aishop.repository.KnowledgeDocumentRepository;
import com.aishop.repository.OrderItemRepository;
import com.aishop.repository.ProductRepository;
import com.aishop.repository.ShopOrderRepository;

@Service
public class AdminService {

    private final AppUserRepository userRepository;
    private final ProductRepository productRepository;
    private final ShopOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final ProductService productService;
    private final KnowledgeService knowledgeService;

    public AdminService(AppUserRepository userRepository,
                        ProductRepository productRepository,
                        ShopOrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        KnowledgeDocumentRepository knowledgeDocumentRepository,
                        ProductService productService,
                        KnowledgeService knowledgeService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.productService = productService;
        this.knowledgeService = knowledgeService;
    }

    @Transactional(readOnly = true)
    public DashboardMetricResponse dashboard() {
        List<ShopOrder> orders = orderRepository.findAllByOrderByCreatedAtDesc();
        BigDecimal totalRevenue = orders.stream()
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
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
    public AdminOrderResponse updateOrderStatus(Long id, String rawStatus) {
        ShopOrder order = orderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        try {
            order.setStatus(OrderStatus.valueOf(rawStatus));
        } catch (Exception ex) {
            throw new IllegalArgumentException("不支持的订单状态: " + rawStatus);
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
}

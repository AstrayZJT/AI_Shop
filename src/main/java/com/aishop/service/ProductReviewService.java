package com.aishop.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.domain.AppUser;
import com.aishop.domain.OrderItem;
import com.aishop.domain.OrderStatus;
import com.aishop.domain.Product;
import com.aishop.domain.ProductReview;
import com.aishop.dto.ProductDtos.ProductReviewRequest;
import com.aishop.dto.ProductDtos.ProductReviewResponse;
import com.aishop.repository.OrderItemRepository;
import com.aishop.repository.ProductRepository;
import com.aishop.repository.ProductReviewRepository;
import com.aishop.repository.ShopOrderRepository;

@Service
public class ProductReviewService {

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final ShopOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderTimelineService orderTimelineService;

    public ProductReviewService(ProductReviewRepository reviewRepository,
                                ProductRepository productRepository,
                                ShopOrderRepository orderRepository,
                                OrderItemRepository orderItemRepository,
                                OrderTimelineService orderTimelineService) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderTimelineService = orderTimelineService;
    }

    @Transactional(readOnly = true)
    public List<ProductReviewResponse> listProductReviews(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("商品不存在"));
        return reviewRepository.findByProductOrderByCreatedAtDesc(product).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductReviewResponse> listRecentReviews() {
        return reviewRepository.findTop30ByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProductReviewResponse reviewOrderItem(AppUser user, Long orderId, Long itemId, ProductReviewRequest request) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!order.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("无权评价该订单");
        }
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new IllegalArgumentException("只有已完成订单可以评价");
        }
        OrderItem orderItem = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("订单商品不存在"));
        if (!orderItem.getOrder().getId().equals(order.getId())) {
            throw new IllegalArgumentException("订单商品不属于该订单");
        }
        Product product = productRepository.findBySku(orderItem.getProductSku())
                .orElseThrow(() -> new IllegalArgumentException("商品已下架或不存在"));
        int rating = normalizeRating(request == null ? null : request.rating());
        String content = normalizeContent(request == null ? null : request.content());

        ProductReview review = reviewRepository.findByOrderItemAndUser(orderItem, user).orElseGet(ProductReview::new);
        boolean created = review.getId() == null;
        review.setProduct(product);
        review.setUser(user);
        review.setOrder(order);
        review.setOrderItem(orderItem);
        review.setRating(rating);
        review.setContent(content);
        ProductReview saved = reviewRepository.save(review);

        orderTimelineService.recordCustomerEvent(
                order,
                created ? "ORDER_ITEM_REVIEWED" : "ORDER_ITEM_REVIEW_UPDATED",
                created ? "用户发表商品评价" : "用户更新商品评价",
                "%s 评分 %s 星，评价：%s".formatted(orderItem.getProductName(), rating, content));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Double averageRating(Product product) {
        Double average = reviewRepository.averageRatingByProduct(product);
        if (average == null) {
            return null;
        }
        return BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    @Transactional(readOnly = true)
    public long reviewCount(Product product) {
        return reviewRepository.countByProduct(product);
    }

    @Transactional(readOnly = true)
    public String reviewSummary(Product product) {
        List<ProductReview> reviews = reviewRepository.findTop3ByProductOrderByCreatedAtDesc(product);
        if (reviews.isEmpty()) {
            return null;
        }
        String joined = reviews.stream()
                .map(review -> "%s星：%s".formatted(review.getRating(), trim(review.getContent(), 42)))
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
        return joined.isBlank() ? null : joined;
    }

    public ProductReviewResponse toResponse(ProductReview review) {
        Product product = review.getProduct();
        AppUser user = review.getUser();
        return new ProductReviewResponse(
                review.getId(),
                product.getId(),
                product.getSku(),
                product.getName(),
                review.getOrder().getOrderNo(),
                user.getUsername(),
                user.getDisplayName(),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt());
    }

    private int normalizeRating(Integer rating) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("评分必须在 1 到 5 星之间");
        }
        return rating;
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("评价内容不能为空");
        }
        String normalized = content.trim();
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("评价内容不能超过 500 字");
        }
        return normalized;
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}

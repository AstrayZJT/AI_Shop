package com.aishop.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.domain.AppUser;
import com.aishop.domain.CustomerProductEvent;
import com.aishop.domain.Product;
import com.aishop.dto.ProductDtos.ProductEventRequest;
import com.aishop.dto.ProductDtos.ProductEventResponse;
import com.aishop.dto.ProductDtos.ProductResponse;
import com.aishop.repository.CustomerProductEventRepository;

@Service
public class CustomerBehaviorService {

    public static final String EVENT_VIEW = "VIEW";
    public static final String EVENT_AI_CONSULT = "AI_CONSULT";
    public static final String EVENT_ADD_TO_CART = "ADD_TO_CART";
    public static final String EVENT_FAVORITE = "FAVORITE";
    public static final String EVENT_UNFAVORITE = "UNFAVORITE";
    public static final String EVENT_CHECKOUT = "CHECKOUT";

    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            EVENT_VIEW,
            EVENT_AI_CONSULT,
            EVENT_ADD_TO_CART,
            EVENT_FAVORITE,
            EVENT_UNFAVORITE,
            EVENT_CHECKOUT);

    private final CustomerProductEventRepository eventRepository;
    private final ProductService productService;

    public CustomerBehaviorService(CustomerProductEventRepository eventRepository,
                                   ProductService productService) {
        this.eventRepository = eventRepository;
        this.productService = productService;
    }

    @Transactional
    public ProductEventResponse recordEvent(AppUser user, ProductEventRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("行为事件不能为空");
        }
        return recordEvent(user, request.productId(), request.eventType(), request.source(), request.detail(), request.quantity());
    }

    @Transactional
    public ProductEventResponse recordEvent(AppUser user,
                                            Long productId,
                                            String eventType,
                                            String source,
                                            String detail,
                                            Integer quantity) {
        if (user == null) {
            throw new IllegalArgumentException("请先登录");
        }
        Product product = productService.getProduct(productId);
        CustomerProductEvent event = new CustomerProductEvent();
        event.setUser(user);
        event.setProduct(product);
        event.setEventType(normalizeEventType(eventType));
        event.setSource(trimToNull(source));
        event.setDetail(trimToNull(detail));
        event.setQuantity(quantity);
        return toResponse(eventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public List<ProductEventResponse> recentEvents(AppUser user, int limit) {
        if (user == null) {
            return List.of();
        }
        return eventRepository.findTop20ByUserOrderByCreatedAtDesc(user).stream()
                .limit(Math.max(0, limit))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> recentInterestedProducts(AppUser user, int limit) {
        if (user == null) {
            return List.of();
        }
        Set<Long> seenProductIds = new LinkedHashSet<>();
        return eventRepository.findTop20ByUserOrderByCreatedAtDesc(user).stream()
                .filter(event -> !EVENT_UNFAVORITE.equals(event.getEventType()))
                .filter(event -> seenProductIds.add(event.getProduct().getId()))
                .limit(Math.max(0, limit))
                .map(CustomerProductEvent::getProduct)
                .map(productService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long count(AppUser user) {
        return user == null ? 0L : eventRepository.countByUser(user);
    }

    @Transactional(readOnly = true)
    public long countByType(AppUser user, String eventType) {
        if (user == null) {
            return 0L;
        }
        return eventRepository.countByUserAndEventType(user, normalizeEventType(eventType));
    }

    @Transactional(readOnly = true)
    public String summarizeRecentBehavior(AppUser user, int limit) {
        return recentEvents(user, limit).stream()
                .map(event -> "%s%s(%s%s)".formatted(
                        eventLabel(event.eventType()),
                        event.product() == null ? "商品" : event.product().name(),
                        event.product() == null ? "未知价格" : event.product().price(),
                        event.quantity() == null || event.quantity() <= 1 ? "" : "，数量" + event.quantity()))
                .reduce((left, right) -> left + " | " + right)
                .orElse("暂无近期商品行为");
    }

    @Transactional(readOnly = true)
    public boolean seededEventExists(AppUser user, Product product, String eventType, String source) {
        return eventRepository.existsByUserAndProductAndEventTypeAndSource(
                user,
                product,
                normalizeEventType(eventType),
                source);
    }

    private ProductEventResponse toResponse(CustomerProductEvent event) {
        return new ProductEventResponse(
                event.getId(),
                event.getEventType(),
                event.getSource(),
                event.getDetail(),
                event.getQuantity(),
                event.getCreatedAt(),
                productService.toResponse(event.getProduct()));
    }

    private String normalizeEventType(String value) {
        String normalized = value == null || value.isBlank()
                ? EVENT_VIEW
                : value.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_EVENTS.contains(normalized)) {
            throw new IllegalArgumentException("不支持的商品行为类型: " + value);
        }
        return normalized;
    }

    private String eventLabel(String eventType) {
        return switch (eventType) {
            case EVENT_AI_CONSULT -> "咨询过";
            case EVENT_ADD_TO_CART -> "加购过";
            case EVENT_FAVORITE -> "收藏过";
            case EVENT_UNFAVORITE -> "取消收藏";
            case EVENT_CHECKOUT -> "结算过";
            default -> "浏览过";
        };
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

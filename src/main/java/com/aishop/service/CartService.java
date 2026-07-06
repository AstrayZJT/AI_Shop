package com.aishop.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.domain.AppUser;
import com.aishop.domain.Cart;
import com.aishop.domain.CartItem;
import com.aishop.domain.OrderItem;
import com.aishop.domain.OrderStatus;
import com.aishop.domain.Product;
import com.aishop.domain.ShopOrder;
import com.aishop.dto.CartDtos.CartItemResponse;
import com.aishop.dto.CartDtos.CartResponse;
import com.aishop.dto.OrderDtos.OrderResponse;
import com.aishop.repository.CartItemRepository;
import com.aishop.repository.CartRepository;
import com.aishop.repository.OrderItemRepository;
import com.aishop.repository.ShopOrderRepository;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductService productService;
    private final ShopOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderService orderService;
    private final OrderTimelineService orderTimelineService;
    private final PromotionService promotionService;
    private final ShippingAddressService shippingAddressService;
    private final CustomerBehaviorService behaviorService;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       ProductService productService,
                       ShopOrderRepository orderRepository,
                       OrderItemRepository orderItemRepository,
                       OrderService orderService,
                       OrderTimelineService orderTimelineService,
                       PromotionService promotionService,
                       ShippingAddressService shippingAddressService,
                       CustomerBehaviorService behaviorService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productService = productService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderService = orderService;
        this.orderTimelineService = orderTimelineService;
        this.promotionService = promotionService;
        this.shippingAddressService = shippingAddressService;
        this.behaviorService = behaviorService;
    }

    @Transactional(readOnly = true)
    public CartResponse currentCart(AppUser user) {
        return cartRepository.findByUserAndCheckedOutFalse(user)
                .map(this::toResponse)
                .orElse(new CartResponse(null, 0, BigDecimal.ZERO, List.of()));
    }

    @Transactional
    public CartResponse addItem(AppUser user, Long productId, Integer quantity) {
        int safeQuantity = quantity == null || quantity <= 0 ? 1 : quantity;
        Product product = productService.getProduct(productId);
        if (product.getStock() < safeQuantity) {
            throw new IllegalArgumentException("商品库存不足: " + product.getName());
        }

        Cart cart = getOrCreateCart(user);
        CartItem item = cartItemRepository.findByCartAndProduct(cart, product).orElseGet(() -> {
            CartItem created = new CartItem();
            created.setCart(cart);
            created.setProduct(product);
            created.setQuantity(0);
            created.setUnitPrice(product.getPrice());
            return created;
        });
        int nextQuantity = item.getQuantity() + safeQuantity;
        if (product.getStock() < nextQuantity) {
            throw new IllegalArgumentException("加入购物车后库存不足: " + product.getName());
        }
        item.setQuantity(nextQuantity);
        item.setUnitPrice(product.getPrice());
        cartItemRepository.save(item);
        behaviorService.recordEvent(user, productId, CustomerBehaviorService.EVENT_ADD_TO_CART, "cart-api", null, safeQuantity);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItem(AppUser user, Long itemId, Integer quantity) {
        Cart cart = getOrCreateCart(user);
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("购物车商品不存在"));
        if (!Objects.equals(item.getCart().getId(), cart.getId())) {
            throw new IllegalArgumentException("无权修改该购物车商品");
        }
        if (quantity == null || quantity <= 0) {
            cartItemRepository.delete(item);
            return toResponse(cart);
        }
        Product product = productService.getProduct(item.getProduct().getId());
        if (product.getStock() < quantity) {
            throw new IllegalArgumentException("商品库存不足: " + product.getName());
        }
        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());
        cartItemRepository.save(item);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(AppUser user, Long itemId) {
        Cart cart = getOrCreateCart(user);
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("购物车商品不存在"));
        if (!Objects.equals(item.getCart().getId(), cart.getId())) {
            throw new IllegalArgumentException("无权删除该购物车商品");
        }
        cartItemRepository.delete(item);
        return toResponse(cart);
    }

    @Transactional
    public OrderResponse checkout(AppUser user, String shippingAddress, String promotionCode, Long addressId) {
        Cart cart = cartRepository.findByUserAndCheckedOutFalse(user)
                .orElseThrow(() -> new IllegalArgumentException("购物车为空"));
        List<CartItem> items = cartItemRepository.findByCart(cart);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("购物车为空");
        }

        BigDecimal originalAmount = BigDecimal.ZERO;
        for (CartItem item : items) {
            Product product = productService.getProduct(item.getProduct().getId());
            productService.decreaseStock(product, item.getQuantity());
            originalAmount = originalAmount.add(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        originalAmount = originalAmount.setScale(2, RoundingMode.HALF_UP);
        PromotionService.PromotionCalculation promotionCalculation = promotionService.resolvePromotion(promotionCode, originalAmount);

        ShopOrder order = new ShopOrder();
        order.setOrderNo("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setOriginalAmount(originalAmount);
        order.setDiscountAmount(promotionCalculation.discountAmount());
        order.setTotalAmount(promotionCalculation.payableAmount());
        order.setPromotionCode(promotionCalculation.campaign() == null ? null : promotionCalculation.campaign().getCode());
        order.setPromotionTitle(promotionCalculation.campaign() == null ? null : promotionCalculation.campaign().getTitle());
        order.setShippingAddress(shippingAddressService.resolveCheckoutAddress(user, addressId, shippingAddress));
        order.setRiskNote(promotionCalculation.discountAmount().compareTo(BigDecimal.ZERO) > 0
                ? "客户端购物车结算创建，已使用优惠后等待支付"
                : "客户端购物车结算创建，等待支付");
        order = orderRepository.save(order);

        for (CartItem item : items) {
            Product product = productService.getProduct(item.getProduct().getId());
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProductName(product.getName());
            orderItem.setProductSku(product.getSku());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setUnitPrice(product.getPrice());
            orderItem.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            orderItemRepository.save(orderItem);
            behaviorService.recordEvent(
                    user,
                    product.getId(),
                    CustomerBehaviorService.EVENT_CHECKOUT,
                    "cart-checkout",
                    order.getOrderNo(),
                    item.getQuantity());
        }

        cart.setCheckedOut(true);
        cartRepository.save(cart);
        cartItemRepository.deleteByCart(cart);
        orderTimelineService.recordCustomerEvent(
                order,
                "ORDER_CREATED",
                "用户提交购物车订单",
                buildCheckoutTimelineDetail(
                        items.size(),
                        originalAmount,
                        promotionCalculation.discountAmount(),
                        promotionCalculation.payableAmount(),
                        order.getPromotionCode(),
                        order.getPromotionTitle()));
        return orderService.toResponse(order);
    }

    private Cart getOrCreateCart(AppUser user) {
        return cartRepository.findByUserAndCheckedOutFalse(user).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setUser(user);
            cart.setCheckedOut(false);
            return cartRepository.save(cart);
        });
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCart(cart);
        List<CartItemResponse> itemResponses = items.stream().map(item -> {
            Product product = item.getProduct();
            BigDecimal lineTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            return new CartItemResponse(
                    item.getId(),
                    product.getId(),
                    product.getName(),
                    product.getImageUrl(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    lineTotal);
        }).toList();
        int totalItems = itemResponses.stream().mapToInt(CartItemResponse::quantity).sum();
        BigDecimal totalAmount = itemResponses.stream()
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(cart.getId(), totalItems, totalAmount, itemResponses);
    }

    private String buildCheckoutTimelineDetail(int itemCount,
                                               BigDecimal originalAmount,
                                               BigDecimal discountAmount,
                                               BigDecimal payableAmount,
                                               String promotionCode,
                                               String promotionTitle) {
        if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return "共 " + itemCount + " 件商品，结算金额 " + originalAmount + "，当前待支付。";
        }
        String promotionLabel = promotionTitle == null || promotionTitle.isBlank()
                ? promotionCode
                : promotionTitle + " (" + promotionCode + ")";
        return "共 " + itemCount + " 件商品，原价 " + originalAmount + "，优惠 " + discountAmount
                + "，实付 " + payableAmount + "，使用活动 " + promotionLabel + "。";
    }
}

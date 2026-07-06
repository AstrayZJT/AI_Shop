package com.aishop.service;

import java.math.BigDecimal;
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

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       ProductService productService,
                       ShopOrderRepository orderRepository,
                       OrderItemRepository orderItemRepository,
                       OrderService orderService,
                       OrderTimelineService orderTimelineService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productService = productService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderService = orderService;
        this.orderTimelineService = orderTimelineService;
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
    public OrderResponse checkout(AppUser user, String shippingAddress) {
        Cart cart = cartRepository.findByUserAndCheckedOutFalse(user)
                .orElseThrow(() -> new IllegalArgumentException("购物车为空"));
        List<CartItem> items = cartItemRepository.findByCart(cart);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("购物车为空");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CartItem item : items) {
            Product product = productService.getProduct(item.getProduct().getId());
            productService.decreaseStock(product, item.getQuantity());
            totalAmount = totalAmount.add(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        ShopOrder order = new ShopOrder();
        order.setOrderNo("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(totalAmount);
        order.setShippingAddress((shippingAddress == null || shippingAddress.isBlank())
                ? (user.getShippingAddress() == null ? "待补充收货地址" : user.getShippingAddress())
                : shippingAddress);
        order.setRiskNote("客户端购物车结算创建，等待支付");
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
        }

        cart.setCheckedOut(true);
        cartRepository.save(cart);
        cartItemRepository.deleteByCart(cart);
        orderTimelineService.recordCustomerEvent(
                order,
                "ORDER_CREATED",
                "用户提交购物车订单",
                "共 %s 件商品，结算金额 %s，当前待支付。".formatted(items.size(), totalAmount));
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
}

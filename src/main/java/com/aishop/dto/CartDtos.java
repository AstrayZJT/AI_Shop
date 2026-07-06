package com.aishop.dto;

import java.math.BigDecimal;
import java.util.List;

public final class CartDtos {
    private CartDtos() {
    }

    public record CartItemRequest(Long productId, Integer quantity) {}
    public record UpdateCartItemRequest(Integer quantity) {}
    public record CartItemResponse(Long itemId,
                                   Long productId,
                                   String productName,
                                   String imageUrl,
                                   Integer quantity,
                                   BigDecimal unitPrice,
                                   BigDecimal lineTotal) {}
    public record CartResponse(Long cartId,
                               Integer totalItems,
                               BigDecimal totalAmount,
                               List<CartItemResponse> items) {}
    public record CheckoutRequest(String shippingAddress, String promotionCode, Long addressId) {}
}

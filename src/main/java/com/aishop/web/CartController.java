package com.aishop.web;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.dto.CartDtos.CartItemRequest;
import com.aishop.dto.CartDtos.CartResponse;
import com.aishop.dto.CartDtos.CheckoutRequest;
import com.aishop.dto.CartDtos.UpdateCartItemRequest;
import com.aishop.dto.OrderDtos.OrderResponse;
import com.aishop.service.AuthService;
import com.aishop.service.CartService;

import jakarta.servlet.http.HttpSession;

@RestController
public class CartController {

    private final AuthService authService;
    private final CartService cartService;

    public CartController(AuthService authService, CartService cartService) {
        this.authService = authService;
        this.cartService = cartService;
    }

    @GetMapping("/api/cart")
    public CartResponse currentCart(HttpSession session) {
        return cartService.currentCart(authService.requireUser(session));
    }

    @PostMapping("/api/cart/items")
    public CartResponse addItem(HttpSession session, @RequestBody CartItemRequest request) {
        return cartService.addItem(authService.requireUser(session), request.productId(), request.quantity());
    }

    @PatchMapping("/api/cart/items/{itemId}")
    public CartResponse updateItem(HttpSession session,
                                   @PathVariable Long itemId,
                                   @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(authService.requireUser(session), itemId, request.quantity());
    }

    @DeleteMapping("/api/cart/items/{itemId}")
    public CartResponse removeItem(HttpSession session, @PathVariable Long itemId) {
        return cartService.removeItem(authService.requireUser(session), itemId);
    }

    @PostMapping("/api/cart/checkout")
    public OrderResponse checkout(HttpSession session, @RequestBody CheckoutRequest request) {
        return cartService.checkout(authService.requireUser(session), request.shippingAddress(), request.promotionCode(), request.addressId());
    }
}

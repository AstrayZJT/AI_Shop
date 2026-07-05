package com.aishop.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.dto.OrderDtos.OrderActionRequest;
import com.aishop.dto.OrderDtos.OrderDraftRequest;
import com.aishop.dto.OrderDtos.OrderDraftResponse;
import com.aishop.dto.OrderDtos.OrderResponse;
import com.aishop.service.AuthService;
import com.aishop.service.OrderService;

import jakarta.servlet.http.HttpSession;

@RestController
public class OrderController {

    private final AuthService authService;
    private final OrderService orderService;

    public OrderController(AuthService authService, OrderService orderService) {
        this.authService = authService;
        this.orderService = orderService;
    }

    @GetMapping("/api/orders")
    public List<OrderResponse> list(HttpSession session) {
        return orderService.listOrders(authService.requireUser(session));
    }

    @GetMapping("/api/orders/{id}")
    public OrderResponse detail(HttpSession session, @PathVariable Long id) {
        return orderService.detail(authService.requireUser(session), id);
    }

    @PostMapping("/api/orders/draft")
    public OrderDraftResponse draft(HttpSession session, @RequestBody OrderDraftRequest request) {
        return orderService.buildDraft(authService.requireUser(session), request.productId(), request.quantity(), request.threadId());
    }

    @PostMapping("/api/orders/confirm")
    public Object confirm(HttpSession session, @RequestBody OrderDraftRequest request) {
        return orderService.confirmDraft(authService.requireUser(session), request.threadId());
    }

    @PatchMapping("/api/orders/{id}/cancel")
    public OrderResponse cancel(HttpSession session,
                                @PathVariable Long id,
                                @RequestBody(required = false) OrderActionRequest request) {
        return orderService.cancelOrder(authService.requireUser(session), id, request == null ? null : request.note());
    }

    @PatchMapping("/api/orders/{id}/confirm-receipt")
    public OrderResponse confirmReceipt(HttpSession session, @PathVariable Long id) {
        return orderService.confirmReceipt(authService.requireUser(session), id);
    }

    @PatchMapping("/api/orders/{id}/refund")
    public OrderResponse refund(HttpSession session,
                                @PathVariable Long id,
                                @RequestBody(required = false) OrderActionRequest request) {
        return orderService.requestRefund(authService.requireUser(session), id, request == null ? null : request.note());
    }
}

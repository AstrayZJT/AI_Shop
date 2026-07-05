package com.aishop.dto;

import java.math.BigDecimal;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {
    }

    public record OrderItemResponse(String productName, Integer quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
    public record OrderResponse(Long id, String orderNo, String status, BigDecimal totalAmount, String shippingAddress, String riskNote, List<OrderItemResponse> items) {}
    public record OrderDraftRequest(Long productId, Integer quantity, String threadId) {}
    public record OrderDraftResponse(String threadId, String draftJson, String status) {}
    public record PendingOrderDraftResponse(String threadId,
                                            String status,
                                            Long productId,
                                            String productName,
                                            Integer quantity,
                                            BigDecimal unitPrice,
                                            BigDecimal totalAmount,
                                            String note) {}
    public record OrderActionRequest(String note) {}
}

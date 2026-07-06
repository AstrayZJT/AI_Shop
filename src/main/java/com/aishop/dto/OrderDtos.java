package com.aishop.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {
    }

    public record OrderItemResponse(String productName, Integer quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
    public record AfterSalesResponse(String status,
                                     String customerReason,
                                     String adminReply,
                                     boolean returnRequired,
                                     String returnAddress,
                                     String returnCarrier,
                                     String returnTrackingNo,
                                     String returnNote,
                                     Instant requestedAt,
                                     Instant adminRespondedAt,
                                     Instant customerShippedAt,
                                     Instant resolvedAt) {}
    public record OrderTimelineResponse(String eventType,
                                        String title,
                                        String detail,
                                        String actorLabel,
                                        Instant occurredAt) {}
    public record OrderResponse(Long id,
                                String orderNo,
                                String status,
                                BigDecimal totalAmount,
                                String shippingAddress,
                                String shippingCarrier,
                                String trackingNo,
                                Instant shippedAt,
                                String riskNote,
                                List<OrderItemResponse> items,
                                List<OrderTimelineResponse> timeline,
                                AfterSalesResponse afterSales) {}
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
    public record UpdateShippingAddressRequest(String shippingAddress, String note) {}
    public record ReturnShipmentRequest(String carrier, String trackingNo, String note) {}
}

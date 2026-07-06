package com.aishop.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {
    }

    public record OrderItemResponse(Long id,
                                    Long productId,
                                    String productSku,
                                    String productName,
                                    Integer quantity,
                                    BigDecimal unitPrice,
                                    BigDecimal lineTotal,
                                    Long reviewId,
                                    Integer reviewRating,
                                    String reviewContent) {}
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
    public record InvoiceResponse(String status,
                                  String headerType,
                                  String invoiceTitle,
                                  String taxNo,
                                  String email,
                                  String note,
                                  String adminReply,
                                  String invoiceNo,
                                  Instant requestedAt,
                                  Instant reviewedAt,
                                  Instant issuedAt) {}
    public record OrderResponse(Long id,
                                String orderNo,
                                String status,
                                BigDecimal originalAmount,
                                BigDecimal discountAmount,
                                BigDecimal totalAmount,
                                String promotionCode,
                                String promotionTitle,
                                String shippingAddress,
                                String shippingCarrier,
                                String trackingNo,
                                Instant shippedAt,
                                String paymentMethod,
                                String paymentReference,
                                Instant paidAt,
                                String riskNote,
                                List<OrderItemResponse> items,
                                List<OrderTimelineResponse> timeline,
                                AfterSalesResponse afterSales,
                                InvoiceResponse invoice) {}
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
    public record PayOrderRequest(String paymentMethod, String note) {}
    public record UpdateShippingAddressRequest(String shippingAddress, String note) {}
    public record ReturnShipmentRequest(String carrier, String trackingNo, String note) {}
    public record InvoiceRequest(String headerType,
                                 String invoiceTitle,
                                 String taxNo,
                                 String email,
                                 String note) {}
    public record InvoiceReviewRequest(String invoiceNo, String note) {}
}

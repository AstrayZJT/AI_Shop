package com.aishop.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.aishop.dto.KnowledgeDtos.SearchResponse;
import com.aishop.dto.OrderDtos.AfterSalesResponse;
import com.aishop.dto.OrderDtos.OrderTimelineResponse;

public final class AdminDtos {
    private AdminDtos() {
    }

    public record DashboardMetricResponse(long userCount,
                                          long productCount,
                                          long orderCount,
                                          BigDecimal totalRevenue,
                                          long knowledgeDocumentCount,
                                          long lowStockProductCount,
                                          long pendingShipmentCount) {}

    public record ProductUpsertRequest(String sku,
                                       String name,
                                       String description,
                                       BigDecimal price,
                                       Integer stock,
                                       String imageUrl,
                                       String categoryName,
                                       String categoryDescription) {}

    public record AdminOrderItemResponse(String productName,
                                         String productSku,
                                         Integer quantity,
                                         BigDecimal unitPrice,
                                         BigDecimal lineTotal) {}

    public record AdminOrderResponse(Long id,
                                     String orderNo,
                                     String status,
                                     String username,
                                     String displayName,
                                     BigDecimal totalAmount,
                                     String shippingAddress,
                                     String shippingCarrier,
                                     String trackingNo,
                                     Instant shippedAt,
                                     String paymentMethod,
                                     String paymentReference,
                                     Instant paidAt,
                                     String riskNote,
                                     Instant createdAt,
                                     List<AdminOrderItemResponse> items,
                                     List<OrderTimelineResponse> timeline,
                                     AfterSalesResponse afterSales) {}

    public record UpdateOrderStatusRequest(String status, String note, String shippingCarrier, String trackingNo) {}

    public record OrderLogisticsUpdateRequest(String detail) {}

    public record ReturnInstructionRequest(String returnAddress, String reply) {}

    public record RefundReviewRequest(Boolean approved, String note) {}

    public record KnowledgeDocumentResponse(Long id,
                                            String title,
                                            String docType,
                                            String contentPreview,
                                            Instant createdAt) {}

    public record KnowledgeSearchResponse(String keyword,
                                          List<SearchResponse> matches) {}

    public record AdminUserResponse(Long id,
                                    String username,
                                    String displayName,
                                    String role,
                                    String phone,
                                    String shippingAddress,
                                    Instant createdAt) {}

    public record AdminAssistantSessionResponse(Long id,
                                                String threadId,
                                                String title,
                                                String summary,
                                                String lastIntent,
                                                String serviceStatus,
                                                String username,
                                                String displayName,
                                                long messageCount,
                                                String assignedAdminUsername,
                                                String assignedAdminDisplayName,
                                                Instant assignedAt,
                                                Instant firstSupportReplyAt,
                                                Instant resolvedAt,
                                                Instant lastCustomerMessageAt,
                                                Instant lastSupportMessageAt,
                                                long supportUnreadCount,
                                                long customerUnreadCount,
                                                Instant createdAt) {}

    public record AdminAssistantMessageResponse(String role,
                                                String content,
                                                Instant createdAt) {}

    public record AdminAssistantDraftResponse(Long id,
                                              String threadId,
                                              String status,
                                              String username,
                                              String displayName,
                                              Long productId,
                                              String productName,
                                              Integer quantity,
                                              BigDecimal unitPrice,
                                              BigDecimal totalAmount,
                                              String note,
                                              Instant createdAt) {}

    public record AdminAssistantReplyRequest(String content, Boolean resolve) {}

    public record AdminAssistantAssignRequest(String adminUsername) {}

    public record AdminAssistantClaimResponse(Long sessionId,
                                              String serviceStatus,
                                              String assignedAdminUsername,
                                              String assignedAdminDisplayName,
                                              Instant assignedAt) {}
}

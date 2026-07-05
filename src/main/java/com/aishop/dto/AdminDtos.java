package com.aishop.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.aishop.dto.KnowledgeDtos.SearchResponse;

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
                                     String riskNote,
                                     Instant createdAt,
                                     List<AdminOrderItemResponse> items) {}

    public record UpdateOrderStatusRequest(String status, String note) {}

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
}

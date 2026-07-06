package com.aishop.dto;

import java.math.BigDecimal;
import java.time.Instant;

public final class ProductDtos {
    private ProductDtos() {
    }

    public record CategoryResponse(Long id, String name, String description) {}
    public record ProductResponse(Long id,
                                  String sku,
                                  String name,
                                  String description,
                                  BigDecimal price,
                                  Integer stock,
                                  String imageUrl,
                                  String category,
                                  Double averageRating,
                                  Long reviewCount,
                                  String reviewSummary) {}
    public record FavoriteProductResponse(Long favoriteId,
                                          Instant createdAt,
                                          ProductResponse product) {}
    public record ProductReviewRequest(Integer rating, String content) {}
    public record ProductReviewResponse(Long id,
                                        Long productId,
                                        String productSku,
                                        String productName,
                                        String orderNo,
                                        String username,
                                        String displayName,
                                        Integer rating,
                                        String content,
                                        Instant createdAt) {}
}

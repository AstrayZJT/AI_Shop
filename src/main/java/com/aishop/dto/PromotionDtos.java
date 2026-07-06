package com.aishop.dto;

import java.math.BigDecimal;
import java.time.Instant;

public final class PromotionDtos {
    private PromotionDtos() {
    }

    public record PromotionResponse(Long id,
                                    String code,
                                    String title,
                                    String description,
                                    String discountType,
                                    BigDecimal discountValue,
                                    BigDecimal minOrderAmount,
                                    boolean active,
                                    Instant expiresAt,
                                    boolean applicable,
                                    BigDecimal estimatedDiscountAmount,
                                    String applyHint) {}

    public record PromotionUpsertRequest(String code,
                                         String title,
                                         String description,
                                         String discountType,
                                         BigDecimal discountValue,
                                         BigDecimal minOrderAmount,
                                         Boolean active,
                                         Instant expiresAt) {}
}

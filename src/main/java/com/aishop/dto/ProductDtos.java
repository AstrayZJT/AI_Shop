package com.aishop.dto;

import java.math.BigDecimal;

public final class ProductDtos {
    private ProductDtos() {
    }

    public record CategoryResponse(Long id, String name, String description) {}
    public record ProductResponse(Long id, String sku, String name, String description, BigDecimal price, Integer stock, String imageUrl, String category) {}
}

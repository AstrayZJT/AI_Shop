package com.aishop.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.domain.Product;
import com.aishop.domain.ProductCategory;
import com.aishop.dto.ProductDtos.CategoryResponse;
import com.aishop.dto.ProductDtos.ProductResponse;
import com.aishop.repository.ProductCategoryRepository;
import com.aishop.repository.ProductRepository;

@Service
public class ProductService {

    private static final Pattern SEARCH_TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}|[a-z0-9]{2,}");

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductReviewService productReviewService;

    public ProductService(ProductRepository productRepository,
                          ProductCategoryRepository categoryRepository,
                          ProductReviewService productReviewService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productReviewService = productReviewService;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listAll() {
        return productRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return listAll();
        }
        String normalizedKeyword = normalize(keyword);
        List<String> tokens = SEARCH_TOKEN_PATTERN.matcher(normalizedKeyword)
                .results()
                .map(match -> match.group().trim())
                .distinct()
                .toList();

        List<ScoredProduct> scored = productRepository.findAll().stream()
                .map(product -> new ScoredProduct(product, scoreProduct(product, normalizedKeyword, tokens)))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingInt(ScoredProduct::score).reversed()
                        .thenComparing(result -> result.product().getStock(), Comparator.reverseOrder()))
                .toList();
        if (!scored.isEmpty()) {
            return scored.stream().map(result -> toResponse(result.product())).toList();
        }
        return productRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(keyword, keyword)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(category -> new CategoryResponse(category.getId(), category.getName(), category.getDescription()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse detail(Long id) {
        return toResponse(productRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("商品不存在")));
    }

    public Product getProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("商品不存在"));
    }

    @Transactional
    public void decreaseStock(Product product, int quantity) {
        int safeQuantity = Math.max(1, quantity);
        if (product.getStock() < safeQuantity) {
            throw new IllegalArgumentException("商品库存不足: " + product.getName());
        }
        product.setStock(product.getStock() - safeQuantity);
        productRepository.save(product);
    }

    @Transactional
    public void increaseStock(Product product, int quantity) {
        int safeQuantity = Math.max(1, quantity);
        product.setStock(product.getStock() + safeQuantity);
        productRepository.save(product);
    }

    @Transactional
    public void increaseStockBySku(String sku, int quantity) {
        if (sku == null || sku.isBlank()) {
            return;
        }
        productRepository.findBySku(sku.trim())
                .ifPresent(product -> increaseStock(product, quantity));
    }

    @Transactional
    public ProductCategory getOrCreateCategory(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("分类名称不能为空");
        }
        return categoryRepository.findByName(name.trim()).orElseGet(() -> {
            var category = new ProductCategory();
            category.setName(name.trim());
            category.setDescription(description);
            return categoryRepository.save(category);
        });
    }

    ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getImageUrl(),
                product.getCategory() == null ? null : product.getCategory().getName(),
                productReviewService.averageRating(product),
                productReviewService.reviewCount(product),
                productReviewService.reviewSummary(product));
    }

    private int scoreProduct(Product product, String normalizedKeyword, List<String> tokens) {
        String name = normalize(product.getName());
        String description = normalize(product.getDescription());
        String category = normalize(product.getCategory() == null ? null : product.getCategory().getName());
        String reviews = normalize(productReviewService.reviewSummary(product));
        String combined = name + " " + description + " " + category + " " + reviews;

        int score = 0;
        if (!normalizedKeyword.isBlank()) {
            if (name.contains(normalizedKeyword)) {
                score += 260;
            }
            if (combined.contains(normalizedKeyword)) {
                score += 220;
            }
            if (!name.isBlank() && normalizedKeyword.contains(name)) {
                score += 320;
            }
        }
        for (String token : tokens) {
            if (name.contains(token)) {
                score += 72 + token.length() * 14;
            }
            if (description.contains(token)) {
                score += 28 + token.length() * 6;
            }
            if (category.contains(token)) {
                score += 18 + token.length() * 5;
            }
            if (reviews.contains(token)) {
                score += 14 + token.length() * 4;
            }
        }
        long reviewCount = productReviewService.reviewCount(product);
        Double averageRating = productReviewService.averageRating(product);
        if ((normalizedKeyword.contains("口碑") || normalizedKeyword.contains("评价")) && reviewCount > 0) {
            score += 32 + (averageRating == null ? 0 : (int) Math.round(averageRating * 8));
        }
        return score;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String lowered = value.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(lowered.length());
        for (int i = 0; i < lowered.length(); i++) {
            char ch = lowered.charAt(i);
            if (Character.isLetterOrDigit(ch) || isChinese(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private boolean isChinese(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    private record ScoredProduct(Product product, int score) {}
}

package com.aishop.service;

import java.util.List;

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

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, ProductCategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
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
                product.getCategory() == null ? null : product.getCategory().getName());
    }
}

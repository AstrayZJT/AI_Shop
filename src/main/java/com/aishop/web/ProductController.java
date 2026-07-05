package com.aishop.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.dto.ProductDtos.CategoryResponse;
import com.aishop.dto.ProductDtos.ProductResponse;
import com.aishop.service.ProductService;

@RestController
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/api/products")
    public List<ProductResponse> list() {
        return productService.listAll();
    }

    @GetMapping("/api/products/{id}")
    public ProductResponse detail(@PathVariable Long id) {
        return productService.detail(id);
    }

    @GetMapping("/api/products/search")
    public List<ProductResponse> search(@RequestParam String keyword) {
        return productService.search(keyword);
    }

    @GetMapping("/api/categories")
    public List<CategoryResponse> categories() {
        return productService.categories();
    }
}

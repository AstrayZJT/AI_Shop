package com.aishop.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.domain.ProductCategory;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
    Optional<ProductCategory> findByName(String name);
    java.util.List<ProductCategory> findAllByOrderByNameAsc();
}

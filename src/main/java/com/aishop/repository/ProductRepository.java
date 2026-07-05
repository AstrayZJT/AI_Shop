package com.aishop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.aishop.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @EntityGraph(attributePaths = "category")
    List<Product> findAll();

    @EntityGraph(attributePaths = "category")
    List<Product> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String name, String description);

    @EntityGraph(attributePaths = "category")
    Optional<Product> findById(Long id);

    Optional<Product> findBySku(String sku);

    @EntityGraph(attributePaths = "category")
    List<Product> findByStockLessThanEqualOrderByStockAsc(Integer stock);
}

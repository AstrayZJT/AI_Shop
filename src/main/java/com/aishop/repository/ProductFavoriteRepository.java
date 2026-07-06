package com.aishop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.domain.AppUser;
import com.aishop.domain.Product;
import com.aishop.domain.ProductFavorite;

public interface ProductFavoriteRepository extends JpaRepository<ProductFavorite, Long> {
    List<ProductFavorite> findByUserOrderByCreatedAtDesc(AppUser user);
    List<ProductFavorite> findTop6ByUserOrderByCreatedAtDesc(AppUser user);
    Optional<ProductFavorite> findByUserAndProduct(AppUser user, Product product);
    boolean existsByUserAndProduct(AppUser user, Product product);
    long countByUser(AppUser user);
    void deleteByUserAndProduct(AppUser user, Product product);
}

package com.aishop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aishop.domain.AppUser;
import com.aishop.domain.OrderItem;
import com.aishop.domain.Product;
import com.aishop.domain.ProductReview;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    @EntityGraph(attributePaths = {"product", "user", "order", "orderItem"})
    List<ProductReview> findByProductOrderByIdDesc(Product product);

    @EntityGraph(attributePaths = {"product", "user", "order", "orderItem"})
    List<ProductReview> findTop30ByOrderByIdDesc();

    Optional<ProductReview> findByOrderItemAndUser(OrderItem orderItem, AppUser user);

    long countByProduct(Product product);

    @Query("select avg(r.rating) from ProductReview r where r.product = :product")
    Double averageRatingByProduct(@Param("product") Product product);

    @EntityGraph(attributePaths = {"user"})
    List<ProductReview> findTop3ByProductOrderByIdDesc(Product product);
}

package com.aishop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.domain.Cart;
import com.aishop.domain.CartItem;
import com.aishop.domain.Product;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCart(Cart cart);
    void deleteByCart(Cart cart);
    Optional<CartItem> findByCartAndProduct(Cart cart, Product product);
}

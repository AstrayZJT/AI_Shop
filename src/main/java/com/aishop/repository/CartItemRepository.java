package com.aishop.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.domain.Cart;
import com.aishop.domain.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCart(Cart cart);
    void deleteByCart(Cart cart);
}

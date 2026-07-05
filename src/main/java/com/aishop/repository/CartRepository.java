package com.aishop.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.domain.AppUser;
import com.aishop.domain.Cart;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUserAndCheckedOutFalse(AppUser user);
}

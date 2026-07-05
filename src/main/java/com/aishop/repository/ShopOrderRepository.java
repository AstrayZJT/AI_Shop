package com.aishop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.domain.AppUser;
import com.aishop.domain.ShopOrder;

public interface ShopOrderRepository extends JpaRepository<ShopOrder, Long> {
    Optional<ShopOrder> findByOrderNo(String orderNo);
    List<ShopOrder> findByUserOrderByCreatedAtDesc(AppUser user);
    List<ShopOrder> findAllByOrderByCreatedAtDesc();
}

package com.aishop.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.domain.OrderItem;
import com.aishop.domain.ShopOrder;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrder(ShopOrder order);
}

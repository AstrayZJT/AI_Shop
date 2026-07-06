package com.aishop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.domain.OrderTimelineEvent;
import com.aishop.domain.ShopOrder;

public interface OrderTimelineEventRepository extends JpaRepository<OrderTimelineEvent, Long> {

    boolean existsByOrder(ShopOrder order);

    List<OrderTimelineEvent> findByOrderOrderByOccurredAtAscIdAsc(ShopOrder order);

    Optional<OrderTimelineEvent> findFirstByOrderOrderByOccurredAtDescIdDesc(ShopOrder order);
}

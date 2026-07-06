package com.aishop.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.domain.AppUser;
import com.aishop.domain.CustomerProductEvent;
import com.aishop.domain.Product;

public interface CustomerProductEventRepository extends JpaRepository<CustomerProductEvent, Long> {
    List<CustomerProductEvent> findTop20ByUserOrderByCreatedAtDesc(AppUser user);
    List<CustomerProductEvent> findByUserOrderByCreatedAtDesc(AppUser user);
    long countByUser(AppUser user);
    long countByUserAndEventType(AppUser user, String eventType);
    boolean existsByUserAndProductAndEventTypeAndSource(AppUser user, Product product, String eventType, String source);
}

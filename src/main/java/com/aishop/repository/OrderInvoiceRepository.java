package com.aishop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.domain.OrderInvoice;
import com.aishop.domain.ShopOrder;

public interface OrderInvoiceRepository extends JpaRepository<OrderInvoice, Long> {
    Optional<OrderInvoice> findByOrder(ShopOrder order);
    List<OrderInvoice> findByStatusOrderByRequestedAtAsc(String status);
}

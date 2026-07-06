package com.aishop.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.domain.AfterSalesCase;
import com.aishop.domain.ShopOrder;

public interface AfterSalesCaseRepository extends JpaRepository<AfterSalesCase, Long> {

    Optional<AfterSalesCase> findByOrder(ShopOrder order);
}

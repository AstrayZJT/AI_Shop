package com.aishop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.domain.PromotionCampaign;

public interface PromotionCampaignRepository extends JpaRepository<PromotionCampaign, Long> {
    Optional<PromotionCampaign> findByCodeIgnoreCase(String code);
    List<PromotionCampaign> findAllByOrderByIdDesc();
}

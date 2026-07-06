package com.aishop.web;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.dto.PromotionDtos.PromotionResponse;
import com.aishop.service.PromotionService;

@RestController
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @GetMapping("/api/promotions")
    public List<PromotionResponse> promotions(@RequestParam(required = false) BigDecimal subtotal) {
        return promotionService.listAvailable(subtotal == null ? BigDecimal.ZERO : subtotal);
    }
}

package com.aishop.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.domain.PromotionCampaign;
import com.aishop.dto.PromotionDtos.PromotionResponse;
import com.aishop.dto.PromotionDtos.PromotionUpsertRequest;
import com.aishop.repository.PromotionCampaignRepository;

@Service
public class PromotionService {

    public static final String DISCOUNT_TYPE_FIXED = "FIXED";
    public static final String DISCOUNT_TYPE_PERCENT = "PERCENT";

    private final PromotionCampaignRepository promotionCampaignRepository;

    public PromotionService(PromotionCampaignRepository promotionCampaignRepository) {
        this.promotionCampaignRepository = promotionCampaignRepository;
    }

    @Transactional(readOnly = true)
    public List<PromotionResponse> listAvailable(BigDecimal subtotalAmount) {
        BigDecimal subtotal = safeMoney(subtotalAmount);
        return promotionCampaignRepository.findAllByOrderByIdDesc().stream()
                .filter(this::isVisibleToClient)
                .map(promotion -> toResponse(promotion, subtotal))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PromotionResponse> listAll() {
        return promotionCampaignRepository.findAllByOrderByIdDesc().stream()
                .map(promotion -> toResponse(promotion, BigDecimal.ZERO))
                .toList();
    }

    @Transactional
    public PromotionResponse create(PromotionUpsertRequest request) {
        PromotionCampaign campaign = new PromotionCampaign();
        applyFields(campaign, request, null);
        return toResponse(promotionCampaignRepository.save(campaign), BigDecimal.ZERO);
    }

    @Transactional
    public PromotionResponse update(Long id, PromotionUpsertRequest request) {
        PromotionCampaign campaign = promotionCampaignRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("促销活动不存在"));
        applyFields(campaign, request, id);
        return toResponse(promotionCampaignRepository.save(campaign), BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public PromotionCalculation resolvePromotion(String code, BigDecimal subtotalAmount) {
        BigDecimal subtotal = safeMoney(subtotalAmount);
        String normalizedCode = normalizeCode(code);
        if (normalizedCode == null) {
            return new PromotionCalculation(null, subtotal, BigDecimal.ZERO, "未使用优惠码");
        }
        PromotionCampaign campaign = promotionCampaignRepository.findByCodeIgnoreCase(normalizedCode)
                .orElseThrow(() -> new IllegalArgumentException("优惠码不存在"));
        if (!Boolean.TRUE.equals(campaign.getActive())) {
            throw new IllegalArgumentException("优惠码当前不可用");
        }
        if (isExpired(campaign)) {
            throw new IllegalArgumentException("优惠码已过期");
        }
        BigDecimal minimum = safeMoney(campaign.getMinOrderAmount());
        if (subtotal.compareTo(minimum) < 0) {
            throw new IllegalArgumentException("当前购物车金额尚未达到优惠门槛");
        }
        BigDecimal discountAmount = calculateDiscountAmount(campaign, subtotal);
        if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("当前购物车暂不满足该优惠码条件");
        }
        BigDecimal payableAmount = subtotal.subtract(discountAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new PromotionCalculation(campaign, payableAmount, discountAmount, buildApplyHint(campaign, subtotal, discountAmount, true));
    }

    @Transactional
    public void seedPromotion(String code,
                              String title,
                              String description,
                              String discountType,
                              String discountValue,
                              String minOrderAmount,
                              boolean active,
                              Instant expiresAt) {
        PromotionCampaign campaign = promotionCampaignRepository.findByCodeIgnoreCase(code).orElseGet(PromotionCampaign::new);
        campaign.setCode(normalizeCode(code));
        campaign.setTitle(title);
        campaign.setDescription(description);
        campaign.setDiscountType(normalizeDiscountType(discountType));
        campaign.setDiscountValue(new BigDecimal(discountValue).setScale(2, RoundingMode.HALF_UP));
        campaign.setMinOrderAmount(minOrderAmount == null ? null : new BigDecimal(minOrderAmount).setScale(2, RoundingMode.HALF_UP));
        campaign.setActive(active);
        campaign.setExpiresAt(expiresAt);
        promotionCampaignRepository.save(campaign);
    }

    private void applyFields(PromotionCampaign campaign, PromotionUpsertRequest request, Long currentId) {
        if (request == null) {
            throw new IllegalArgumentException("促销信息不能为空");
        }
        String code = normalizeCode(request.code());
        if (code == null) {
            throw new IllegalArgumentException("优惠码不能为空");
        }
        String title = blankToNull(request.title());
        if (title == null) {
            throw new IllegalArgumentException("活动标题不能为空");
        }
        String discountType = normalizeDiscountType(request.discountType());
        BigDecimal discountValue = safePositiveMoney(request.discountValue(), "优惠力度必须大于 0");
        BigDecimal minOrderAmount = request.minOrderAmount() == null
                ? BigDecimal.ZERO
                : safeMoney(request.minOrderAmount());
        promotionCampaignRepository.findByCodeIgnoreCase(code).ifPresent(existing -> {
            if (currentId == null || !existing.getId().equals(currentId)) {
                throw new IllegalArgumentException("优惠码已存在");
            }
        });

        if (DISCOUNT_TYPE_PERCENT.equals(discountType) && discountValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("百分比优惠不能超过 100");
        }

        campaign.setCode(code);
        campaign.setTitle(title);
        campaign.setDescription(blankToNull(request.description()));
        campaign.setDiscountType(discountType);
        campaign.setDiscountValue(discountValue.setScale(2, RoundingMode.HALF_UP));
        campaign.setMinOrderAmount(minOrderAmount.setScale(2, RoundingMode.HALF_UP));
        campaign.setActive(Boolean.TRUE.equals(request.active()));
        campaign.setExpiresAt(request.expiresAt());
    }

    private PromotionResponse toResponse(PromotionCampaign campaign, BigDecimal subtotalAmount) {
        BigDecimal subtotal = safeMoney(subtotalAmount);
        boolean visible = isVisibleToClient(campaign);
        boolean applicable = visible && subtotal.compareTo(BigDecimal.ZERO) > 0 && isApplicableToSubtotal(campaign, subtotal);
        BigDecimal estimatedDiscount = applicable ? calculateDiscountAmount(campaign, subtotal) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return new PromotionResponse(
                campaign.getId(),
                campaign.getCode(),
                campaign.getTitle(),
                campaign.getDescription(),
                campaign.getDiscountType(),
                safeMoney(campaign.getDiscountValue()),
                safeMoney(campaign.getMinOrderAmount()),
                Boolean.TRUE.equals(campaign.getActive()),
                campaign.getExpiresAt(),
                applicable,
                estimatedDiscount,
                buildApplyHint(campaign, subtotal, estimatedDiscount, applicable));
    }

    private boolean isVisibleToClient(PromotionCampaign campaign) {
        return Boolean.TRUE.equals(campaign.getActive()) && !isExpired(campaign);
    }

    private boolean isApplicableToSubtotal(PromotionCampaign campaign, BigDecimal subtotal) {
        if (!isVisibleToClient(campaign)) {
            return false;
        }
        return subtotal.compareTo(safeMoney(campaign.getMinOrderAmount())) >= 0
                && calculateDiscountAmount(campaign, subtotal).compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal calculateDiscountAmount(PromotionCampaign campaign, BigDecimal subtotal) {
        BigDecimal rawAmount;
        if (DISCOUNT_TYPE_PERCENT.equals(campaign.getDiscountType())) {
            rawAmount = subtotal.multiply(safeMoney(campaign.getDiscountValue()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            rawAmount = safeMoney(campaign.getDiscountValue());
        }
        return rawAmount.min(subtotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private String buildApplyHint(PromotionCampaign campaign, BigDecimal subtotal, BigDecimal discountAmount, boolean applicable) {
        BigDecimal minimum = safeMoney(campaign.getMinOrderAmount());
        if (!Boolean.TRUE.equals(campaign.getActive())) {
            return "活动已停用";
        }
        if (isExpired(campaign)) {
            return "活动已过期";
        }
        if (subtotal.compareTo(minimum) < 0) {
            return "还差 " + formatMoney(minimum.subtract(subtotal)) + " 可用";
        }
        if (applicable) {
            return "预计可减 " + formatMoney(discountAmount);
        }
        return "当前金额暂不可用";
    }

    private boolean isExpired(PromotionCampaign campaign) {
        return campaign.getExpiresAt() != null && campaign.getExpiresAt().isBefore(Instant.now());
    }

    private String normalizeCode(String code) {
        String normalized = blankToNull(code);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeDiscountType(String discountType) {
        String normalized = blankToNull(discountType);
        if (normalized == null) {
            throw new IllegalArgumentException("优惠类型不能为空");
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!DISCOUNT_TYPE_FIXED.equals(upper) && !DISCOUNT_TYPE_PERCENT.equals(upper)) {
            throw new IllegalArgumentException("优惠类型只支持 FIXED 或 PERCENT");
        }
        return upper;
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal safePositiveMoney(BigDecimal value, String errorMessage) {
        BigDecimal normalized = safeMoney(value);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String formatMoney(BigDecimal value) {
        return "¥" + safeMoney(value).toPlainString();
    }

    public record PromotionCalculation(PromotionCampaign campaign,
                                       BigDecimal payableAmount,
                                       BigDecimal discountAmount,
                                       String applyHint) {}
}

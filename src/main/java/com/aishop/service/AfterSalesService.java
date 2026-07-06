package com.aishop.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.domain.AfterSalesCase;
import com.aishop.domain.OrderStatus;
import com.aishop.domain.ShopOrder;
import com.aishop.dto.OrderDtos.AfterSalesResponse;
import com.aishop.repository.AfterSalesCaseRepository;

@Service
public class AfterSalesService {

    public static final String STATUS_REQUESTED = "REQUESTED";
    public static final String STATUS_AWAITING_CUSTOMER_RETURN = "AWAITING_CUSTOMER_RETURN";
    public static final String STATUS_RETURN_SHIPPED = "RETURN_SHIPPED";
    public static final String STATUS_REFUNDED = "REFUNDED";
    public static final String STATUS_REJECTED = "REJECTED";

    private final AfterSalesCaseRepository afterSalesCaseRepository;

    public AfterSalesService(AfterSalesCaseRepository afterSalesCaseRepository) {
        this.afterSalesCaseRepository = afterSalesCaseRepository;
    }

    @Transactional
    public AfterSalesCase submitRefundRequest(ShopOrder order, String customerReason) {
        AfterSalesCase afterSalesCase = getOrCreate(order);
        afterSalesCase.setStatus(STATUS_REQUESTED);
        afterSalesCase.setCustomerReason(blankToNull(customerReason));
        afterSalesCase.setRequestedAt(Instant.now());
        afterSalesCase.setResolvedAt(null);
        afterSalesCase.setAdminReply(null);
        afterSalesCase.setAdminRespondedAt(null);
        afterSalesCase.setReturnRequired(Boolean.FALSE);
        afterSalesCase.setReturnAddress(null);
        afterSalesCase.setReturnCarrier(null);
        afterSalesCase.setReturnTrackingNo(null);
        afterSalesCase.setReturnNote(null);
        afterSalesCase.setCustomerShippedAt(null);
        return afterSalesCaseRepository.save(afterSalesCase);
    }

    @Transactional
    public AfterSalesCase approveWithReturn(ShopOrder order, String returnAddress, String adminReply) {
        AfterSalesCase afterSalesCase = requireCase(order);
        afterSalesCase.setStatus(STATUS_AWAITING_CUSTOMER_RETURN);
        afterSalesCase.setReturnRequired(Boolean.TRUE);
        afterSalesCase.setReturnAddress(requireText(returnAddress, "请填写退货回寄地址"));
        afterSalesCase.setAdminReply(requireText(adminReply, "请填写退货指引说明"));
        afterSalesCase.setAdminRespondedAt(Instant.now());
        afterSalesCase.setResolvedAt(null);
        return afterSalesCaseRepository.save(afterSalesCase);
    }

    @Transactional
    public AfterSalesCase submitReturnShipment(ShopOrder order, String carrier, String trackingNo, String note) {
        AfterSalesCase afterSalesCase = requireCase(order);
        if (!STATUS_AWAITING_CUSTOMER_RETURN.equals(afterSalesCase.getStatus())) {
            throw new IllegalArgumentException("当前售后工单还没有进入待用户回寄状态");
        }
        afterSalesCase.setStatus(STATUS_RETURN_SHIPPED);
        afterSalesCase.setReturnCarrier(requireText(carrier, "请填写回寄物流公司"));
        afterSalesCase.setReturnTrackingNo(requireText(trackingNo, "请填写回寄运单号"));
        afterSalesCase.setReturnNote(blankToNull(note));
        afterSalesCase.setCustomerShippedAt(Instant.now());
        return afterSalesCaseRepository.save(afterSalesCase);
    }

    @Transactional
    public AfterSalesCase markRefunded(ShopOrder order, String adminReply) {
        AfterSalesCase afterSalesCase = getOrCreate(order);
        afterSalesCase.setStatus(STATUS_REFUNDED);
        if (blankToNull(adminReply) != null) {
            afterSalesCase.setAdminReply(adminReply.trim());
        }
        afterSalesCase.setAdminRespondedAt(Instant.now());
        afterSalesCase.setResolvedAt(Instant.now());
        return afterSalesCaseRepository.save(afterSalesCase);
    }

    @Transactional
    public AfterSalesCase markRejected(ShopOrder order, String adminReply) {
        AfterSalesCase afterSalesCase = getOrCreate(order);
        afterSalesCase.setStatus(STATUS_REJECTED);
        afterSalesCase.setAdminReply(blankToNull(adminReply));
        afterSalesCase.setAdminRespondedAt(Instant.now());
        afterSalesCase.setResolvedAt(Instant.now());
        return afterSalesCaseRepository.save(afterSalesCase);
    }

    @Transactional
    public AfterSalesCase getCase(ShopOrder order) {
        return ensureCaseFromOrderState(order);
    }

    @Transactional
    public AfterSalesResponse toResponse(ShopOrder order) {
        return java.util.Optional.ofNullable(ensureCaseFromOrderState(order))
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public AfterSalesResponse toResponse(AfterSalesCase afterSalesCase) {
        if (afterSalesCase == null) {
            return null;
        }
        return new AfterSalesResponse(
                normalizeStatus(afterSalesCase.getStatus()),
                blankToNull(afterSalesCase.getCustomerReason()),
                blankToNull(afterSalesCase.getAdminReply()),
                Boolean.TRUE.equals(afterSalesCase.getReturnRequired()),
                blankToNull(afterSalesCase.getReturnAddress()),
                blankToNull(afterSalesCase.getReturnCarrier()),
                blankToNull(afterSalesCase.getReturnTrackingNo()),
                blankToNull(afterSalesCase.getReturnNote()),
                afterSalesCase.getRequestedAt(),
                afterSalesCase.getAdminRespondedAt(),
                afterSalesCase.getCustomerShippedAt(),
                afterSalesCase.getResolvedAt());
    }

    private AfterSalesCase getOrCreate(ShopOrder order) {
        return afterSalesCaseRepository.findByOrder(order).orElseGet(() -> {
            AfterSalesCase created = new AfterSalesCase();
            created.setOrder(order);
            created.setStatus(STATUS_REQUESTED);
            return created;
        });
    }

    private AfterSalesCase ensureCaseFromOrderState(ShopOrder order) {
        AfterSalesCase existing = afterSalesCaseRepository.findByOrder(order).orElse(null);
        if (existing != null) {
            return existing;
        }
        if (order.getStatus() != OrderStatus.REFUND_REQUESTED && order.getStatus() != OrderStatus.REFUNDED) {
            return null;
        }
        AfterSalesCase created = new AfterSalesCase();
        created.setOrder(order);
        created.setCustomerReason(blankToNull(order.getRiskNote()));
        created.setRequestedAt(order.getUpdatedAt() == null ? order.getCreatedAt() : order.getUpdatedAt());
        if (order.getStatus() == OrderStatus.REFUNDED) {
            created.setStatus(STATUS_REFUNDED);
            created.setResolvedAt(order.getUpdatedAt() == null ? Instant.now() : order.getUpdatedAt());
        } else {
            created.setStatus(STATUS_REQUESTED);
        }
        return afterSalesCaseRepository.save(created);
    }

    private AfterSalesCase requireCase(ShopOrder order) {
        return afterSalesCaseRepository.findByOrder(order)
                .orElseThrow(() -> new IllegalArgumentException("当前订单还没有售后工单"));
    }

    private String requireText(String value, String errorMessage) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeStatus(String status) {
        return status == null || status.isBlank() ? STATUS_REQUESTED : status.trim().toUpperCase();
    }
}

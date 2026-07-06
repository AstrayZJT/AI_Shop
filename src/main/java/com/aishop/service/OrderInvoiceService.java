package com.aishop.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.domain.OrderInvoice;
import com.aishop.domain.OrderStatus;
import com.aishop.domain.ShopOrder;
import com.aishop.dto.OrderDtos.InvoiceRequest;
import com.aishop.dto.OrderDtos.InvoiceResponse;
import com.aishop.repository.OrderInvoiceRepository;

@Service
public class OrderInvoiceService {

    public static final String STATUS_REQUESTED = "REQUESTED";
    public static final String STATUS_ISSUED = "ISSUED";
    public static final String STATUS_REJECTED = "REJECTED";

    public static final String HEADER_PERSONAL = "PERSONAL";
    public static final String HEADER_COMPANY = "COMPANY";

    private final OrderInvoiceRepository invoiceRepository;

    public OrderInvoiceService(OrderInvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional
    public InvoiceResponse requestInvoice(ShopOrder order, InvoiceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("发票申请不能为空");
        }
        validateOrderEligibility(order);
        var existingInvoice = invoiceRepository.findByOrder(order);
        if (existingInvoice.isPresent() && STATUS_REQUESTED.equals(normalizeStatus(existingInvoice.get().getStatus()))) {
            throw new IllegalArgumentException("当前订单已经提交过发票申请，请等待平台处理");
        }
        OrderInvoice invoice = existingInvoice.orElseGet(() -> {
            OrderInvoice created = new OrderInvoice();
            created.setOrder(order);
            return created;
        });

        String headerType = normalizeHeaderType(request.headerType());
        invoice.setStatus(STATUS_REQUESTED);
        invoice.setHeaderType(headerType);
        invoice.setInvoiceTitle(requireText(request.invoiceTitle(), "请填写发票抬头"));
        invoice.setTaxNo(HEADER_COMPANY.equals(headerType)
                ? requireText(request.taxNo(), "企业抬头请填写税号")
                : trimToNull(request.taxNo()));
        invoice.setEmail(requireText(request.email(), "请填写接收邮箱"));
        invoice.setNote(trimToNull(request.note()));
        invoice.setAdminReply(null);
        invoice.setInvoiceNo(null);
        invoice.setRequestedAt(Instant.now());
        invoice.setReviewedAt(null);
        invoice.setIssuedAt(null);
        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceResponse issueInvoice(ShopOrder order, String invoiceNo, String adminReply) {
        OrderInvoice invoice = requirePendingInvoice(order);
        invoice.setStatus(STATUS_ISSUED);
        invoice.setInvoiceNo(blankToDefault(invoiceNo, buildInvoiceNo(order)));
        invoice.setAdminReply(trimToNull(adminReply));
        invoice.setReviewedAt(Instant.now());
        invoice.setIssuedAt(Instant.now());
        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public InvoiceResponse rejectInvoice(ShopOrder order, String adminReply) {
        OrderInvoice invoice = requirePendingInvoice(order);
        invoice.setStatus(STATUS_REJECTED);
        invoice.setAdminReply(requireText(adminReply, "请填写驳回原因"));
        invoice.setReviewedAt(Instant.now());
        invoice.setIssuedAt(null);
        invoice.setInvoiceNo(null);
        return toResponse(invoiceRepository.save(invoice));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse toResponse(ShopOrder order) {
        return invoiceRepository.findByOrder(order)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Optional<OrderInvoice> findEntity(ShopOrder order) {
        return invoiceRepository.findByOrder(order);
    }

    @Transactional
    public OrderInvoice seedInvoice(ShopOrder order,
                                    String status,
                                    String headerType,
                                    String invoiceTitle,
                                    String taxNo,
                                    String email,
                                    String note,
                                    String adminReply,
                                    String invoiceNo,
                                    Instant requestedAt,
                                    Instant reviewedAt,
                                    Instant issuedAt) {
        OrderInvoice invoice = invoiceRepository.findByOrder(order).orElseGet(() -> {
            OrderInvoice created = new OrderInvoice();
            created.setOrder(order);
            return created;
        });
        invoice.setStatus(normalizeStatus(status));
        invoice.setHeaderType(normalizeHeaderType(headerType));
        invoice.setInvoiceTitle(invoiceTitle);
        invoice.setTaxNo(trimToNull(taxNo));
        invoice.setEmail(email);
        invoice.setNote(trimToNull(note));
        invoice.setAdminReply(trimToNull(adminReply));
        invoice.setInvoiceNo(trimToNull(invoiceNo));
        invoice.setRequestedAt(requestedAt);
        invoice.setReviewedAt(reviewedAt);
        invoice.setIssuedAt(issuedAt);
        return invoiceRepository.save(invoice);
    }

    private InvoiceResponse toResponse(OrderInvoice invoice) {
        return new InvoiceResponse(
                normalizeStatus(invoice.getStatus()),
                normalizeHeaderType(invoice.getHeaderType()),
                invoice.getInvoiceTitle(),
                invoice.getTaxNo(),
                invoice.getEmail(),
                invoice.getNote(),
                invoice.getAdminReply(),
                invoice.getInvoiceNo(),
                invoice.getRequestedAt(),
                invoice.getReviewedAt(),
                invoice.getIssuedAt());
    }

    private void validateOrderEligibility(ShopOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("已取消订单不能申请发票");
        }
        if (order.getPaidAt() == null) {
            throw new IllegalArgumentException("请先完成支付，再申请发票");
        }
    }

    private OrderInvoice requirePendingInvoice(ShopOrder order) {
        OrderInvoice invoice = invoiceRepository.findByOrder(order)
                .orElseThrow(() -> new IllegalArgumentException("当前订单还没有待处理发票申请"));
        if (!STATUS_REQUESTED.equals(normalizeStatus(invoice.getStatus()))) {
            throw new IllegalArgumentException("当前订单没有待处理的发票申请");
        }
        return invoice;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return STATUS_REQUESTED;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case STATUS_REQUESTED, STATUS_ISSUED, STATUS_REJECTED -> normalized;
            default -> throw new IllegalArgumentException("不支持的发票状态: " + status);
        };
    }

    private String normalizeHeaderType(String headerType) {
        if (headerType == null || headerType.isBlank()) {
            return HEADER_PERSONAL;
        }
        String normalized = headerType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case HEADER_PERSONAL, HEADER_COMPANY -> normalized;
            default -> throw new IllegalArgumentException("不支持的发票抬头类型: " + headerType);
        };
    }

    private String buildInvoiceNo(ShopOrder order) {
        return "INV-" + order.getOrderNo() + "-" + System.currentTimeMillis();
    }

    private String requireText(String value, String errorMessage) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }
}

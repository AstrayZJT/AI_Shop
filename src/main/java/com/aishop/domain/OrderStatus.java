package com.aishop.domain;

public enum OrderStatus {
    DRAFT,
    PENDING_CONFIRMATION,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    COMPLETED,
    REFUND_REQUESTED,
    REFUNDED,
    CANCELLED
}

package com.aishop.assistant.context;

import java.math.BigDecimal;

public record AuthoritativeOrderFact(
        String orderNo,
        String status,
        BigDecimal totalAmount,
        String shippingCarrier,
        String trackingNo) {
}

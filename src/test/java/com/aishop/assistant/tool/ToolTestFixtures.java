package com.aishop.assistant.tool;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.aishop.domain.AppUser;
import com.aishop.dto.OrderDtos.OrderResponse;
import com.aishop.dto.OrderDtos.OrderTimelineResponse;
import com.aishop.dto.ProductDtos.ProductResponse;

final class ToolTestFixtures {

    private ToolTestFixtures() {
    }

    static AppUser user(long id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername("user-" + id);
        return user;
    }

    static ToolContext context() {
        return new ToolContext(user(1L), "request-1");
    }

    static ProductResponse product(long id, String name, String price, int stock, String category) {
        return new ProductResponse(
                id,
                "SKU-" + id,
                name,
                name + " description",
                new BigDecimal(price),
                stock,
                null,
                category,
                4.8,
                12L,
                "评价不错");
    }

    static OrderResponse order(String orderNo, String status) {
        return new OrderResponse(
                10L,
                orderNo,
                status,
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                new BigDecimal("1000.00"),
                null,
                null,
                "上海市测试路 1 号",
                "顺丰",
                "SF123",
                Instant.parse("2026-07-01T00:00:00Z"),
                null,
                null,
                null,
                null,
                List.of(),
                List.of(new OrderTimelineResponse(
                        "SHIPPED", "订单已发货", "包裹已交给承运商", "系统", Instant.parse("2026-07-01T00:00:00Z"))),
                null,
                null);
    }
}

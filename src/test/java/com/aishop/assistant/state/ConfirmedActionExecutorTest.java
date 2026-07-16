package com.aishop.assistant.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.domain.AppUser;
import com.aishop.dto.OrderDtos.OrderResponse;
import com.aishop.service.OrderService;

class ConfirmedActionExecutorTest {

    @Test
    void rechecksOrderByCurrentUserAndUsesFreshOrderIdBeforeCancel() {
        OrderService orderService = mock(OrderService.class);
        ConfirmedActionExecutor executor = new ConfirmedActionExecutor(orderService);
        AppUser user = new AppUser();
        user.setId(1L);
        OrderResponse current = order(88L, "ORD-12345678", "PENDING_PAYMENT");
        OrderResponse cancelled = order(88L, "ORD-12345678", "CANCELLED");
        when(orderService.findByOrderNo(user, "ORD-12345678")).thenReturn(current);
        when(orderService.cancelOrder(user, 88L, "不需要了", "AI 客服")).thenReturn(cancelled);

        var outcome = executor.execute(
                user,
                AssistantAction.CANCEL_ORDER,
                Map.of("orderNo", "ORD-12345678", "note", "不需要了"));

        assertThat(outcome.message()).isEqualTo("订单取消成功");
        assertThat(outcome.data().get("order")).asString().contains("CANCELLED");
        verify(orderService).findByOrderNo(user, "ORD-12345678");
        verify(orderService).cancelOrder(user, 88L, "不需要了", "AI 客服");
    }

    private OrderResponse order(Long id, String orderNo, String status) {
        return new OrderResponse(
                id, orderNo, status,
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN,
                null, null, "测试地址", null, null, null,
                null, null, null, null, List.of(), List.of(), null, null);
    }
}

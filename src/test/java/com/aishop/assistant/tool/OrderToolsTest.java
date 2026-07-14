package com.aishop.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aishop.assistant.tool.tools.PrepareCancelOrderTool;
import com.aishop.assistant.tool.tools.QueryLogisticsTool;
import com.aishop.assistant.tool.tools.QueryOrderTool;
import com.aishop.service.OrderService;

class OrderToolsTest {

    @Test
    void queryOrderUsesCurrentUserAndReturnsSummary() {
        OrderService orderService = mock(OrderService.class);
        ToolContext context = ToolTestFixtures.context();
        when(orderService.findByOrderNo(context.user(), "ORD-12345678"))
                .thenReturn(ToolTestFixtures.order("ORD-12345678", "CONFIRMED"));
        QueryOrderTool tool = new QueryOrderTool(orderService);

        PreparedToolCall call = tool.prepare(context, Map.of("orderNo", "ord-12345678"));
        ToolExecutionOutcome outcome = tool.execute(context, call);

        assertThat(call.targetRef()).isEqualTo("ORD-12345678");
        assertThat(outcome.data()).containsKey("order");
        verify(orderService, times(2)).findByOrderNo(context.user(), "ORD-12345678");
    }

    @Test
    void queryLogisticsReturnsTimeline() {
        OrderService orderService = mock(OrderService.class);
        ToolContext context = ToolTestFixtures.context();
        when(orderService.findByOrderNo(context.user(), "ORD-12345678"))
                .thenReturn(ToolTestFixtures.order("ORD-12345678", "SHIPPED"));
        QueryLogisticsTool tool = new QueryLogisticsTool(orderService);

        PreparedToolCall call = tool.prepare(context, Map.of("orderNo", "ORD-12345678"));
        ToolExecutionOutcome outcome = tool.execute(context, call);

        assertThat(outcome.message()).isEqualTo("物流查询成功");
        assertThat(outcome.data()).containsKey("logistics");
    }

    @Test
    void preparesCancellationWithoutExecutingIt() {
        OrderService orderService = mock(OrderService.class);
        ToolContext context = ToolTestFixtures.context();
        when(orderService.findByOrderNo(context.user(), "ORD-12345678"))
                .thenReturn(ToolTestFixtures.order("ORD-12345678", "PROCESSING"));
        PrepareCancelOrderTool tool = new PrepareCancelOrderTool(orderService);

        PreparedToolCall call = tool.prepare(context, Map.of("orderNo", "ORD-12345678"));

        assertThat(call.riskLevel()).isEqualTo(ToolRiskLevel.PREPARE_ONLY);
        assertThat(call.preview()).containsEntry("requiresConfirmation", true);
        assertThatThrownBy(() -> tool.execute(context, call))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("禁止自动执行");
    }

    @Test
    void refusesToPrepareCancellationForShippedOrder() {
        OrderService orderService = mock(OrderService.class);
        ToolContext context = ToolTestFixtures.context();
        when(orderService.findByOrderNo(context.user(), "ORD-12345678"))
                .thenReturn(ToolTestFixtures.order("ORD-12345678", "SHIPPED"));
        PrepareCancelOrderTool tool = new PrepareCancelOrderTool(orderService);

        assertThatThrownBy(() -> tool.prepare(context, Map.of("orderNo", "ORD-12345678")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持取消");
    }

    @Test
    void propagatesOrderOwnershipFailureFromBusinessService() {
        OrderService orderService = mock(OrderService.class);
        ToolContext context = ToolTestFixtures.context();
        when(orderService.findByOrderNo(context.user(), "ORD-OTHER001"))
                .thenThrow(new IllegalArgumentException("无权访问该订单"));
        QueryOrderTool tool = new QueryOrderTool(orderService);

        assertThatThrownBy(() -> tool.prepare(context, Map.of("orderNo", "ORD-OTHER001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无权访问");
    }
}

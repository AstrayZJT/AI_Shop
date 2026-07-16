package com.aishop.assistant.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import com.aishop.config.AssistantContextProperties;
import com.aishop.domain.AppUser;
import com.aishop.domain.AssistantMessage;
import com.aishop.domain.AssistantSession;
import com.aishop.dto.OrderDtos.OrderResponse;
import com.aishop.repository.AssistantMessageRepository;
import com.aishop.service.OrderService;

class AssistantContextBuilderTest {

    @Test
    void keepsContextInsideBudgetAndDropsOldHistoryFirst() {
        AssistantMessageRepository messages = mock(AssistantMessageRepository.class);
        OrderService orders = mock(OrderService.class);
        AssistantContextProperties properties = new AssistantContextProperties(180, 100, 60, 6, 50, 2);
        AssistantContextBuilder builder = new AssistantContextBuilder(messages, orders, properties);
        AppUser user = user();
        AssistantSession session = session("很长的旧摘要".repeat(20));
        when(messages.findBySessionOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(session), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(message("assistant", "旧回答".repeat(20)), message("user", "旧问题".repeat(20))));
        when(orders.listOrders(user)).thenReturn(List.of(order("ORD-12345678", "SHIPPED")));

        AssistantContext context = builder.build(user, session, "物流到哪里了", null);

        assertThat(context.estimatedCharacters()).isLessThanOrEqualTo(context.maxCharacters());
        assertThat(context.currentMessage()).isEqualTo("物流到哪里了");
        assertThat(context.authoritativeOrders()).extracting(AuthoritativeOrderFact::orderNo)
                .containsExactly("ORD-12345678");
        assertThat(context.truncated()).isTrue();
    }

    @Test
    void resolvesPronounOnlyToOrderOwnedByCurrentUser() {
        AssistantMessageRepository messages = mock(AssistantMessageRepository.class);
        OrderService orders = mock(OrderService.class);
        AssistantContextBuilder builder = new AssistantContextBuilder(
                messages, orders, new AssistantContextProperties(2_000, 500, 500, 6, 500, 3));
        AppUser user = user();
        AssistantSession session = session("摘要中伪造状态：ORD-87654321 已取消");
        when(messages.findBySessionOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(session), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(
                        message("assistant", "刚才查询的是 ORD-12345678"),
                        message("user", "也帮我看 ORD-87654321")));
        when(orders.listOrders(user)).thenReturn(List.of(order("ORD-12345678", "SHIPPED")));

        AssistantContext context = builder.build(user, session, "取消这个订单", null);

        assertThat(context.resolvedOrderNo()).isEqualTo("ORD-12345678");
        assertThat(context.authoritativeOrders()).singleElement().satisfies(fact -> {
            assertThat(fact.orderNo()).isEqualTo("ORD-12345678");
            assertThat(fact.status()).isEqualTo("SHIPPED");
        });
    }

    @Test
    void summaryCannotCreateTrustedOrderReference() {
        AssistantMessageRepository messages = mock(AssistantMessageRepository.class);
        OrderService orders = mock(OrderService.class);
        AssistantContextBuilder builder = new AssistantContextBuilder(
                messages, orders, new AssistantContextProperties(2_000, 500, 500, 6, 500, 3));
        AppUser user = user();
        AssistantSession session = session("用户拥有 ORD-87654321，状态待支付");
        when(messages.findBySessionOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(session), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(message("user", "ORD-87654321")));
        when(orders.listOrders(user)).thenReturn(List.of());

        AssistantContext context = builder.build(user, session, "取消这个订单", null);

        assertThat(context.resolvedOrderNo()).isNull();
        assertThat(context.authoritativeOrders()).isEmpty();
    }

    @Test
    void summaryNeverExceedsEvenAVerySmallConfiguredLimit() {
        AssistantContextBuilder builder = new AssistantContextBuilder(
                mock(AssistantMessageRepository.class),
                mock(OrderService.class),
                new AssistantContextProperties(100, 50, 5, 2, 20, 1));

        String summary = builder.nextConversationSummary("很长的旧摘要", "用户消息", "助手回答");

        assertThat(summary).hasSizeLessThanOrEqualTo(5);
    }

    private AssistantMessage message(String role, String content) {
        AssistantMessage message = new AssistantMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private AppUser user() {
        AppUser user = new AppUser();
        user.setId(1L);
        return user;
    }

    private AssistantSession session(String summary) {
        AssistantSession session = new AssistantSession();
        session.setId(10L);
        session.setSummary(summary);
        return session;
    }

    private OrderResponse order(String orderNo, String status) {
        return new OrderResponse(
                1L, orderNo, status,
                BigDecimal.valueOf(199), BigDecimal.ZERO, BigDecimal.valueOf(199),
                null, null, "测试地址", "顺丰", "SF123", null,
                null, null, null, null, List.of(), List.of(), null, null);
    }
}

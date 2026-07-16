package com.aishop.assistant.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.aishop.config.AssistantContextProperties;
import com.aishop.domain.AppUser;
import com.aishop.domain.AssistantMessage;
import com.aishop.domain.AssistantSession;
import com.aishop.dto.OrderDtos.OrderResponse;
import com.aishop.repository.AssistantMessageRepository;
import com.aishop.service.OrderService;

@Service
public class AssistantContextBuilder {

    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("ORD-[A-Z0-9]{8}", Pattern.CASE_INSENSITIVE);
    private static final Set<String> ORDER_REFERENCES = Set.of(
            "这个订单", "该订单", "那笔订单", "刚才的订单", "上一个订单", "刚才那个", "这笔订单");

    private final AssistantMessageRepository messageRepository;
    private final OrderService orderService;
    private final AssistantContextProperties properties;

    public AssistantContextBuilder(AssistantMessageRepository messageRepository,
                                   OrderService orderService,
                                   AssistantContextProperties properties) {
        this.messageRepository = messageRepository;
        this.orderService = orderService;
        this.properties = properties;
    }

    public AssistantContext build(AppUser user,
                                  AssistantSession session,
                                  String currentMessage,
                                  String unfinishedPlanSummary) {
        if (user == null || session == null) {
            throw new IllegalArgumentException("用户和会话不能为空");
        }
        if (currentMessage == null || currentMessage.isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }
        String normalizedMessage = currentMessage.strip();
        if (normalizedMessage.length() > properties.maxCurrentMessageCharacters()) {
            throw new IllegalArgumentException(
                    "message 不能超过 " + properties.maxCurrentMessageCharacters() + " 字符");
        }

        List<AssistantMessage> newestMessages = messageRepository.findBySessionOrderByCreatedAtDesc(
                session, PageRequest.of(0, properties.maxRecentMessages()));
        List<OrderResponse> userOrders = orderService.listOrders(user);
        String resolvedOrderNo = resolveOrderReference(normalizedMessage, newestMessages, userOrders);

        Budget budget = new Budget(properties.maxCharacters());
        budget.addRequired(normalizedMessage);

        List<AuthoritativeOrderFact> orderFacts = selectOrderFacts(userOrders, resolvedOrderNo).stream()
                .filter(fact -> budget.tryAdd(orderFactText(fact)))
                .toList();

        String planSummary = budgetedText(unfinishedPlanSummary, properties.maxSummaryCharacters(), budget);
        String conversationSummary = budgetedText(
                session.getSummary(), properties.maxSummaryCharacters(), budget);

        List<ConversationMessage> recentMessages = new ArrayList<>();
        for (AssistantMessage message : newestMessages) {
            String content = clip(message.getContent(), properties.maxMessageCharacters());
            String budgetText = safeRole(message.getRole()) + ": " + content;
            if (!budget.tryAdd(budgetText)) {
                continue;
            }
            recentMessages.add(new ConversationMessage(safeRole(message.getRole()), content));
        }
        Collections.reverse(recentMessages);

        boolean truncated = budget.truncated()
                || newestMessages.size() > recentMessages.size()
                || textWasClipped(session.getSummary(), conversationSummary)
                || textWasClipped(unfinishedPlanSummary, planSummary);
        return new AssistantContext(
                normalizedMessage,
                conversationSummary,
                recentMessages,
                orderFacts,
                planSummary,
                resolvedOrderNo,
                budget.used(),
                properties.maxCharacters(),
                truncated);
    }

    public String nextConversationSummary(String current, String userMessage, String answer) {
        String turn = "U:" + clip(singleLine(userMessage), 160)
                + " A:" + clip(singleLine(answer), 240);
        String combined = current == null || current.isBlank() ? turn : current + " | " + turn;
        if (combined.length() <= properties.maxSummaryCharacters()) {
            return combined;
        }
        String marker = "[较早对话已压缩] ";
        if (properties.maxSummaryCharacters() <= marker.length()) {
            return marker.substring(0, properties.maxSummaryCharacters());
        }
        int tailLength = Math.max(0, properties.maxSummaryCharacters() - marker.length());
        return marker + combined.substring(combined.length() - tailLength);
    }

    private String resolveOrderReference(String currentMessage,
                                         List<AssistantMessage> newestMessages,
                                         List<OrderResponse> userOrders) {
        if (ORDER_NO_PATTERN.matcher(currentMessage).find() || !containsOrderReference(currentMessage)) {
            return null;
        }
        Set<String> ownedOrderNos = userOrders.stream()
                .map(OrderResponse::orderNo)
                .filter(java.util.Objects::nonNull)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (AssistantMessage message : newestMessages) {
            String candidate = lastOwnedOrderNo(message.getContent(), ownedOrderNos);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private String lastOwnedOrderNo(String text, Set<String> ownedOrderNos) {
        Matcher matcher = ORDER_NO_PATTERN.matcher(text == null ? "" : text);
        String selected = null;
        while (matcher.find()) {
            String candidate = matcher.group().toUpperCase(Locale.ROOT);
            if (ownedOrderNos.contains(candidate)) {
                selected = candidate;
            }
        }
        return selected;
    }

    private List<AuthoritativeOrderFact> selectOrderFacts(List<OrderResponse> orders, String resolvedOrderNo) {
        List<OrderResponse> selected = new ArrayList<>();
        if (resolvedOrderNo != null) {
            orders.stream()
                    .filter(order -> resolvedOrderNo.equalsIgnoreCase(order.orderNo()))
                    .findFirst()
                    .ifPresent(selected::add);
        }
        for (OrderResponse order : orders) {
            if (selected.size() >= properties.maxOrderFacts()) {
                break;
            }
            if (selected.stream().noneMatch(existing -> existing.orderNo().equalsIgnoreCase(order.orderNo()))) {
                selected.add(order);
            }
        }
        return selected.stream().map(this::toOrderFact).toList();
    }

    private AuthoritativeOrderFact toOrderFact(OrderResponse order) {
        return new AuthoritativeOrderFact(
                order.orderNo(), order.status(), order.totalAmount(), order.shippingCarrier(), order.trackingNo());
    }

    private String orderFactText(AuthoritativeOrderFact fact) {
        return String.join("|",
                nullToEmpty(fact.orderNo()),
                nullToEmpty(fact.status()),
                fact.totalAmount() == null ? "" : fact.totalAmount().toPlainString(),
                nullToEmpty(fact.shippingCarrier()),
                nullToEmpty(fact.trackingNo()));
    }

    private String budgetedText(String value, int maxLength, Budget budget) {
        if (value == null || value.isBlank() || budget.remaining() <= 0) {
            return null;
        }
        String clipped = clip(value.strip(), Math.min(maxLength, budget.remaining()));
        if (clipped.isEmpty()) {
            return null;
        }
        budget.tryAdd(clipped);
        return clipped;
    }

    private boolean containsOrderReference(String message) {
        String text = message.toLowerCase(Locale.ROOT);
        return ORDER_REFERENCES.stream().anyMatch(text::contains);
    }

    private String safeRole(String role) {
        return role == null || role.isBlank() ? "unknown" : role;
    }

    private String singleLine(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").strip();
    }

    private String clip(String value, int maxLength) {
        if (value == null || maxLength <= 0) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private boolean textWasClipped(String original, String selected) {
        return original != null
                && !original.isBlank()
                && (selected == null || original.strip().length() > selected.length());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class Budget {
        private final int maximum;
        private int used;
        private boolean truncated;

        private Budget(int maximum) {
            this.maximum = maximum;
        }

        private void addRequired(String value) {
            if (!tryAdd(value)) {
                throw new IllegalArgumentException("当前消息超过上下文总预算");
            }
        }

        private boolean tryAdd(String value) {
            int length = value == null ? 0 : value.length();
            if (length > remaining()) {
                truncated = true;
                return false;
            }
            used += length;
            return true;
        }

        private int remaining() {
            return Math.max(0, maximum - used);
        }

        private int used() {
            return used;
        }

        private boolean truncated() {
            return truncated;
        }
    }
}

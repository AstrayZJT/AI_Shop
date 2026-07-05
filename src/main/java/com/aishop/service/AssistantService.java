package com.aishop.service;

import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.domain.AppUser;
import com.aishop.domain.AssistantMessage;
import com.aishop.domain.AssistantSession;
import com.aishop.dto.OrderDtos.OrderResponse;
import com.aishop.dto.ProductDtos.ProductResponse;
import com.aishop.dto.AssistantDtos.ChatResponse;
import com.aishop.repository.AssistantMessageRepository;
import com.aishop.repository.AssistantSessionRepository;

import dev.langchain4j.model.chat.ChatModel;

@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final AssistantSessionRepository sessionRepository;
    private final AssistantMessageRepository messageRepository;
    private final OrderService orderService;
    private final ProductService productService;
    private final KnowledgeService knowledgeService;
    private final CompiledGraph<MessagesState<String>> assistantGraph;
    private final ChatModel chatModel;

    public AssistantService(AssistantSessionRepository sessionRepository,
                            AssistantMessageRepository messageRepository,
                            OrderService orderService,
                            ProductService productService,
                            KnowledgeService knowledgeService,
                            CompiledGraph<MessagesState<String>> assistantGraph,
                            ChatModel chatModel) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.orderService = orderService;
        this.productService = productService;
        this.knowledgeService = knowledgeService;
        this.assistantGraph = assistantGraph;
        this.chatModel = chatModel;
    }

    public List<AssistantSession> listSessions(AppUser user) {
        return sessionRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public AssistantSession getOrCreateSession(AppUser user, Long sessionId) {
        if (sessionId != null) {
            return sessionRepository.findByIdAndUser(sessionId, user)
                    .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        }
        var session = new AssistantSession();
        session.setUser(user);
        session.setTitle("智能助手会话");
        session.setSummary("新会话");
        session.setLastIntent("unknown");
        return sessionRepository.save(session);
    }

    public AssistantSession createSession(AppUser user) {
        return getOrCreateSession(user, null);
    }

    @Transactional
    public ChatResponse chat(AppUser user, Long sessionId, String message, String threadId) {
        if (user == null) {
            throw new IllegalArgumentException("请先登录");
        }

        var session = getOrCreateSession(user, sessionId);
        var currentThreadId = threadId == null || threadId.isBlank() ? "assistant-" + session.getId() : threadId;
        log.info("assistant request: user={}, sessionId={}, threadId={}, message={}",
                user.getUsername(), session.getId(), currentThreadId, preview(message));

        try {
            assistantGraph.invoke(Map.of("messages", List.of(message)), RunnableConfig.builder()
                    .threadId(currentThreadId)
                    .graphId("assistant-workflow")
                    .build());
        } catch (Exception ex) {
            log.warn("assistant graph execution failed, continuing without checkpointed graph state", ex);
        }

        var intent = detectIntent(message);
        var matchedProducts = productService.search(message).stream().limit(3).toList();
        var recentOrders = orderService.listOrders(user).stream().limit(3).toList();
        var sources = knowledgeService.search(message).stream()
                .map(s -> s.title() + ": " + s.chunkText())
                .limit(3)
                .toList();
        log.info("assistant context: sessionId={}, intent={}, sourceCount={}",
                session.getId(), intent, sources.size());

        var answer = buildAnswer(user, intent, sources, matchedProducts, recentOrders, message, currentThreadId);
        var draftJson = buildDraftIfNeeded(user, intent, currentThreadId, message, matchedProducts);
        log.info("assistant response: sessionId={}, intent={}, draftCreated={}, answer={}",
                session.getId(), intent, draftJson != null, preview(answer));

        session.setLastIntent(intent);
        session.setSummary(composeSummary(session.getSummary(), message, answer));
        sessionRepository.save(session);

        saveMessage(session, "user", message);
        saveMessage(session, "assistant", answer);

        return new ChatResponse(session.getId(), answer, intent, currentThreadId, sources, draftJson);
    }

    public List<AssistantMessage> messages(Long sessionId, AppUser user) {
        if (user == null) {
            throw new IllegalArgumentException("请先登录");
        }
        var session = sessionRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        return messageRepository.findBySessionOrderByCreatedAtAsc(session);
    }

    private String buildAnswer(AppUser user,
                               String intent,
                               List<String> sources,
                               List<ProductResponse> matchedProducts,
                               List<OrderResponse> recentOrders,
                               String message,
                               String threadId) {
        if ("order".equals(intent) && isOrderLookupIntent(message)) {
            if (recentOrders.isEmpty()) {
                return "你当前还没有可查询的订单。你可以先加入购物车结算，或者让我帮你生成一个下单草稿。";
            }
            return "我帮你查到了最近的订单：" + formatOrders(recentOrders);
        }
        if ("order".equals(intent) && isPurchaseIntent(message)) {
            var product = matchedProducts.isEmpty() ? productService.listAll().stream().findFirst().orElse(null) : matchedProducts.get(0);
            if (product != null) {
                return "我已经为你准备了下单草稿，推荐商品是：%s，价格 %s。你确认后我就继续创建正式订单。"
                        .formatted(product.name(), product.price());
            }
        }
        if ("product".equals(intent) && !matchedProducts.isEmpty()) {
            if (matchedProducts.size() == 1) {
                var product = matchedProducts.get(0);
                return "我先给你推荐这款商品：%s，价格 %s。%s"
                        .formatted(product.name(), product.price(), product.description());
            }
            return "我帮你筛了几款更相关的商品：" + formatProducts(matchedProducts);
        }

        var prompt = """
                你是一个电商智能助手。
                用户：%s
                当前对话：%s
                用户消息：%s
                意图：%s
                候选商品：%s
                最近订单：%s
                相关知识：%s
                回答要求：优先结合商品、订单、售后规则和知识库，不要编造；如果用户在问订单，就先根据订单上下文回答；如果用户在问商品，就优先引用候选商品；回答要简洁、明确、像客服。
                """.formatted(
                user.getDisplayName(),
                threadId,
                message,
                intent,
                matchedProducts.isEmpty() ? "无" : formatProducts(matchedProducts),
                recentOrders.isEmpty() ? "无" : formatOrders(recentOrders),
                sources.isEmpty() ? "无" : String.join(" | ", sources));

        try {
            return chatModel.chat(prompt);
        } catch (Exception ex) {
            return "我收到了你的问题，正在整理更合适的建议。";
        }
    }

    private String buildDraftIfNeeded(AppUser user,
                                      String intent,
                                      String threadId,
                                      String message,
                                      List<ProductResponse> matchedProducts) {
        if (!"order".equals(intent) || !isPurchaseIntent(message)) {
            return null;
        }
        ProductResponse product = matchedProducts.isEmpty()
                ? productService.listAll().stream().findFirst().orElse(null)
                : matchedProducts.get(0);
        if (product == null) {
            return null;
        }
        return orderService.buildDraft(user, product.id(), 1, threadId).draftJson();
    }

    private String composeSummary(String current, String userMessage, String answer) {
        var base = current == null || current.isBlank() ? "" : current + " | ";
        return base + "U:" + trim(userMessage, 80) + " A:" + trim(answer, 80);
    }

    private String trim(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }

    private String preview(String text) {
        if (text == null) {
            return "";
        }
        return trim(text.replaceAll("\\s+", " ").trim(), 160);
    }

    private void saveMessage(AssistantSession session, String role, String content) {
        var msg = new AssistantMessage();
        msg.setSession(session);
        msg.setRole(role);
        msg.setContent(content);
        messageRepository.save(msg);
    }

    private String detectIntent(String message) {
        var text = message == null ? "" : message.toLowerCase();
        if (text.contains("下单") || text.contains("买") || text.contains("结算") || text.contains("订单") || text.contains("物流") || text.contains("发货")) {
            return "order";
        }
        if (text.contains("推荐") || text.contains("商品") || text.contains("手机") || text.contains("耳机") || text.contains("平板")) {
            return "product";
        }
        if (text.contains("faq") || text.contains("售后") || text.contains("规则") || text.contains("退款") || text.contains("退货") || text.contains("保修")) {
            return "rag";
        }
        return "chat";
    }

    private boolean isPurchaseIntent(String message) {
        String text = message == null ? "" : message.toLowerCase();
        return text.contains("下单") || text.contains("购买") || text.contains("买") || text.contains("来一单") || text.contains("结算");
    }

    private boolean isOrderLookupIntent(String message) {
        String text = message == null ? "" : message.toLowerCase();
        return text.contains("订单") || text.contains("物流") || text.contains("发货") || text.contains("状态") || text.contains("到哪");
    }

    private String formatProducts(List<ProductResponse> products) {
        return products.stream()
                .map(product -> "%s(%s, 库存%s)".formatted(product.name(), product.price(), product.stock()))
                .reduce((left, right) -> left + " | " + right)
                .orElse("无");
    }

    private String formatOrders(List<OrderResponse> orders) {
        return orders.stream()
                .map(order -> "%s[%s, %s]".formatted(order.orderNo(), order.status(), order.totalAmount()))
                .reduce((left, right) -> left + " | " + right)
                .orElse("无");
    }
}

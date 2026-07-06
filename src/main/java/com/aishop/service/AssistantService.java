package com.aishop.service;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final String SERVICE_STATUS_ACTIVE = "ACTIVE";
    private static final String SERVICE_STATUS_ESCALATED = "ESCALATED";
    private static final String SERVICE_STATUS_RESOLVED = "RESOLVED";
    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("ORD-[A-Z0-9]{8}");
    private static final Pattern PURCHASE_QUANTITY_PATTERN = Pattern.compile("(?:买|购买|下单|来|要|购入)\\D{0,4}(\\d{1,2}|一|二|两|三|四|五|六|七|八|九|十)\\s*(?:个|件|台|部|副|双|盒)?");
    private static final Pattern ADDRESS_UPDATE_PATTERN = Pattern.compile("(?:改成|改为|修改为|修改成|更新为|更新成|收货地址为|地址为)(?<address>.+)$");
    private static final Pattern ACTION_NOTE_PATTERN = Pattern.compile("(?:因为|原因是|原因|备注|说明)[：:，,\\s]*(?<note>.+)$");

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
            var session = sessionRepository.findByIdAndUser(sessionId, user)
                    .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
            return normalizeServiceStatus(session);
        }
        var session = new AssistantSession();
        session.setUser(user);
        session.setTitle("智能助手会话");
        session.setSummary("新会话");
        session.setLastIntent("unknown");
        session.setServiceStatus(SERVICE_STATUS_ACTIVE);
        return sessionRepository.save(session);
    }

    public AssistantSession createSession(AppUser user) {
        return getOrCreateSession(user, null);
    }

    @Transactional
    public AssistantSession escalateSession(AppUser user, Long sessionId, String note) {
        if (user == null) {
            throw new IllegalArgumentException("请先登录");
        }
        AssistantSession session = getOrCreateSession(user, sessionId);
        boolean alreadyEscalated = SERVICE_STATUS_ESCALATED.equals(normalizeServiceStatus(session.getServiceStatus()));
        String normalizedNote = blankToNull(note);
        if (normalizedNote != null) {
            saveMessage(session, "user", "申请人工客服：" + normalizedNote);
        }

        markSessionEscalated(session);
        String answer = alreadyEscalated
                ? "你的会话已经在人工跟进中，我把你刚补充的说明也同步给人工客服了。"
                : "我已经帮你转接人工客服，接下来管理员可以在后台直接回复你。";
        session.setSummary(composeSummary(
                session.getSummary(),
                normalizedNote == null ? "申请人工客服" : "申请人工客服：" + normalizedNote,
                answer));
        sessionRepository.save(session);
        saveMessage(session, "assistant", answer);
        return session;
    }

    @Transactional
    public ChatResponse chat(AppUser user, Long sessionId, String message, String threadId) {
        if (user == null) {
            throw new IllegalArgumentException("请先登录");
        }

        var session = getOrCreateSession(user, sessionId);
        if (SERVICE_STATUS_RESOLVED.equals(normalizeServiceStatus(session.getServiceStatus()))) {
            session.setServiceStatus(SERVICE_STATUS_ACTIVE);
        }
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
        var allOrders = orderService.listOrders(user);
        var recentOrders = allOrders.stream().limit(3).toList();
        var exactOrder = findExactMentionedOrder(message, allOrders);
        int requestedQuantity = extractRequestedQuantity(message);
        var sources = knowledgeService.search(message).stream()
                .map(s -> s.title() + ": " + s.chunkText())
                .limit(3)
                .toList();
        log.info("assistant context: sessionId={}, intent={}, sourceCount={}",
                session.getId(), intent, sources.size());

        var actionExecution = executeDirectAction(session, user, message, allOrders, exactOrder);
        if (actionExecution != null) {
            intent = actionExecution.intent();
        }

        var answer = actionExecution == null
                ? buildPendingHandoffAnswer(session, user, intent, sources, matchedProducts, recentOrders, exactOrder, message, currentThreadId)
                : actionExecution.answer();
        var draftJson = actionExecution == null && !SERVICE_STATUS_ESCALATED.equals(normalizeServiceStatus(session.getServiceStatus()))
                ? buildDraftIfNeeded(user, intent, currentThreadId, message, matchedProducts, requestedQuantity)
                : null;
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

    private AssistantSession normalizeServiceStatus(AssistantSession session) {
        String normalized = normalizeServiceStatus(session.getServiceStatus());
        if (!normalized.equals(session.getServiceStatus())) {
            session.setServiceStatus(normalized);
            return sessionRepository.save(session);
        }
        return session;
    }

    private String normalizeServiceStatus(String status) {
        if (status == null || status.isBlank()) {
            return SERVICE_STATUS_ACTIVE;
        }
        return status.trim().toUpperCase();
    }

    private String buildPendingHandoffAnswer(AssistantSession session,
                                             AppUser user,
                                             String intent,
                                             List<String> sources,
                                             List<ProductResponse> matchedProducts,
                                             List<OrderResponse> recentOrders,
                                             OrderResponse exactOrder,
                                             String message,
                                             String threadId) {
        if (SERVICE_STATUS_ESCALATED.equals(normalizeServiceStatus(session.getServiceStatus()))) {
            return "你的会话已经转给人工客服，我会把这条补充信息同步给人工同事，请稍候查看人工回复。";
        }
        return buildAnswer(user, intent, sources, matchedProducts, recentOrders, exactOrder, message, threadId);
    }

    private String buildAnswer(AppUser user,
                               String intent,
                               List<String> sources,
                               List<ProductResponse> matchedProducts,
                               List<OrderResponse> recentOrders,
                               OrderResponse exactOrder,
                               String message,
                               String threadId) {
        OrderResponse focusOrder = findMentionedOrder(message, recentOrders, exactOrder);

        if (isKnowledgePolicyIntent(message) && !hasExplicitOrderReference(message) && !sources.isEmpty()) {
            return buildKnowledgeAnswer(sources);
        }
        if (isCancelIntent(message)) {
            return buildCancelGuide(focusOrder, recentOrders);
        }
        if (isConfirmReceiptIntent(message)) {
            return buildConfirmReceiptGuide(focusOrder, recentOrders);
        }
        if (isRefundIntent(message) && !isPurchaseIntent(message)) {
            return buildRefundGuide(focusOrder, recentOrders, sources);
        }
        if (isAddressIntent(message)) {
            return buildAddressGuide(user, focusOrder);
        }
        if ("order".equals(intent) && isOrderLookupIntent(message)) {
            if (recentOrders.isEmpty()) {
                return "你当前还没有可查询的订单。你可以先加入购物车结算，或者让我帮你生成一个下单草稿。";
            }
            if (focusOrder != null) {
                return "我帮你查到了当前最相关的订单：" + formatOrderDetail(focusOrder);
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
        if (isKnowledgePolicyIntent(message)) {
            return "当前知识库还没有命中这类规则，你可以先在管理端补充售后、退款或物流文档，我再按规则回答你。";
        }

        var prompt = """
                你是一个电商智能助手。
                用户：%s
                当前对话：%s
                用户消息：%s
                意图：%s
                默认收货地址：%s
                候选商品：%s
                最近订单：%s
                相关知识：%s
                回答要求：优先结合商品、订单、售后规则和知识库，不要编造；如果用户在问订单，就先根据订单上下文回答；如果用户在问商品，就优先引用候选商品；如果用户在问地址、取消、退款、确认收货等动作，要结合当前订单状态给出明确步骤；回答要简洁、明确、像客服。
                """.formatted(
                user.getDisplayName(),
                threadId,
                message,
                intent,
                user.getShippingAddress() == null ? "未填写" : user.getShippingAddress(),
                matchedProducts.isEmpty() ? "无" : formatProducts(matchedProducts),
                recentOrders.isEmpty() ? "无" : formatOrders(recentOrders),
                sources.isEmpty() ? "无" : String.join(" | ", sources));

        try {
            return chatModel.chat(prompt);
        } catch (Exception ex) {
            return "我收到了你的问题，正在整理更合适的建议。";
        }
    }

    private ActionExecution executeDirectAction(AssistantSession session,
                                                AppUser user,
                                                String message,
                                                List<OrderResponse> allOrders,
                                                OrderResponse exactOrder) {
        if (message == null || message.isBlank()) {
            return null;
        }
        if (isHumanSupportIntent(message)) {
            if (SERVICE_STATUS_ESCALATED.equals(normalizeServiceStatus(session.getServiceStatus()))) {
                return new ActionExecution("你的会话已经在人工跟进中，我会把这条消息继续同步给人工客服。", "handoff");
            }
            markSessionEscalated(session);
            return new ActionExecution("我已经帮你转接人工客服，管理员接下来可以在后台直接回复你。", "handoff");
        }
        if (isCancelIntent(message) && wantsDirectExecution(message)) {
            if (exactOrder != null) {
                if (!canCancel(exactOrder)) {
                    return new ActionExecution(
                            "订单 %s 当前状态是 %s，暂不支持直接取消。通常只有待发货或处理中订单可以取消。"
                                    .formatted(exactOrder.orderNo(), statusLabel(exactOrder.status())),
                            "order_action");
                }
                OrderResponse updated = orderService.cancelOrder(user, exactOrder.id(), extractActionNote(message, "AI 客服代提交取消申请"));
                return new ActionExecution(
                        "我已经帮你取消订单 %s，当前状态是 %s。".formatted(updated.orderNo(), statusLabel(updated.status())),
                        "order_action");
            }
            OrderResponse candidate = resolveDirectActionOrder(message, allOrders, this::canCancel);
            if (candidate == null) {
                return new ActionExecution(buildActionOrderPrompt("取消订单", eligibleOrders(allOrders, this::canCancel), "你当前没有可取消的订单。"), "order_action");
            }
            OrderResponse updated = orderService.cancelOrder(user, candidate.id(), extractActionNote(message, "AI 客服代提交取消申请"));
            return new ActionExecution(
                    "我已经帮你取消订单 %s，当前状态是 %s。".formatted(updated.orderNo(), statusLabel(updated.status())),
                    "order_action");
        }
        if (isConfirmReceiptIntent(message) && wantsDirectExecution(message)) {
            if (exactOrder != null) {
                if (!canConfirmReceipt(exactOrder)) {
                    return new ActionExecution(
                            "订单 %s 当前状态是 %s，只有已发货订单才能直接确认收货。"
                                    .formatted(exactOrder.orderNo(), statusLabel(exactOrder.status())),
                            "order_action");
                }
                OrderResponse updated = orderService.confirmReceipt(user, exactOrder.id());
                return new ActionExecution(
                        "我已经帮你确认收货，订单 %s 当前状态是 %s。".formatted(updated.orderNo(), statusLabel(updated.status())),
                        "order_action");
            }
            OrderResponse candidate = resolveDirectActionOrder(message, allOrders, this::canConfirmReceipt);
            if (candidate == null) {
                return new ActionExecution(buildActionOrderPrompt("确认收货", eligibleOrders(allOrders, this::canConfirmReceipt), "你当前没有可确认收货的订单。"), "order_action");
            }
            OrderResponse updated = orderService.confirmReceipt(user, candidate.id());
            return new ActionExecution(
                    "我已经帮你确认收货，订单 %s 当前状态是 %s。".formatted(updated.orderNo(), statusLabel(updated.status())),
                    "order_action");
        }
        if (isRefundIntent(message) && wantsDirectExecution(message) && !isPurchaseIntent(message)) {
            if (exactOrder != null) {
                if (!canRefund(exactOrder)) {
                    return new ActionExecution(
                            "订单 %s 当前状态是 %s，暂不支持直接申请退款。通常已发货或已完成订单可以发起退款。"
                                    .formatted(exactOrder.orderNo(), statusLabel(exactOrder.status())),
                            "order_action");
                }
                OrderResponse updated = orderService.requestRefund(user, exactOrder.id(), extractActionNote(message, "AI 客服代提交退款申请"));
                return new ActionExecution(
                        "我已经帮你提交退款申请，订单 %s 当前状态是 %s。平台会继续审核处理。"
                                .formatted(updated.orderNo(), statusLabel(updated.status())),
                        "order_action");
            }
            OrderResponse candidate = resolveDirectActionOrder(message, allOrders, this::canRefund);
            if (candidate == null) {
                return new ActionExecution(buildActionOrderPrompt("申请退款", eligibleOrders(allOrders, this::canRefund), "你当前没有符合退款条件的订单。"), "order_action");
            }
            OrderResponse updated = orderService.requestRefund(user, candidate.id(), extractActionNote(message, "AI 客服代提交退款申请"));
            return new ActionExecution(
                    "我已经帮你提交退款申请，订单 %s 当前状态是 %s。平台会继续审核处理。"
                            .formatted(updated.orderNo(), statusLabel(updated.status())),
                    "order_action");
        }
        if (isAddressIntent(message) && wantsDirectExecution(message)) {
            String newAddress = extractNewShippingAddress(message);
            if (newAddress == null) {
                return new ActionExecution(
                        "我可以直接帮你改订单地址。你把新的收货地址完整发给我就行，例如：帮我把订单 ORD-12345678 的地址改成上海市徐汇区漕溪北路 399 号。",
                        "order_action");
            }
            if (exactOrder != null) {
                if (!canUpdateShippingAddress(exactOrder)) {
                    return new ActionExecution(
                            "订单 %s 当前状态是 %s，暂不支持直接改这个订单的地址。通常只有待发货或处理中订单还能在线改址。"
                                    .formatted(exactOrder.orderNo(), statusLabel(exactOrder.status())),
                            "order_action");
                }
                OrderResponse updated = orderService.updateShippingAddress(user, exactOrder.id(), newAddress, "AI 客服代修改收货地址");
                return new ActionExecution(
                        "我已经帮你把订单 %s 的收货地址改成 %s。当前状态还是 %s。"
                                .formatted(updated.orderNo(), updated.shippingAddress(), statusLabel(updated.status())),
                        "order_action");
            }
            OrderResponse candidate = resolveDirectActionOrder(message, allOrders, this::canUpdateShippingAddress);
            if (candidate == null) {
                return new ActionExecution(buildActionOrderPrompt("修改收货地址", eligibleOrders(allOrders, this::canUpdateShippingAddress), "你当前没有支持改地址的待发货订单。"), "order_action");
            }
            OrderResponse updated = orderService.updateShippingAddress(user, candidate.id(), newAddress, "AI 客服代修改收货地址");
            return new ActionExecution(
                    "我已经帮你把订单 %s 的收货地址改成 %s。当前状态还是 %s。"
                            .formatted(updated.orderNo(), updated.shippingAddress(), statusLabel(updated.status())),
                    "order_action");
        }
        return null;
    }

    private String buildDraftIfNeeded(AppUser user,
                                      String intent,
                                      String threadId,
                                      String message,
                                      List<ProductResponse> matchedProducts,
                                      int requestedQuantity) {
        if (!"order".equals(intent) || !isPurchaseIntent(message)) {
            return null;
        }
        ProductResponse product = matchedProducts.isEmpty()
                ? productService.listAll().stream().findFirst().orElse(null)
                : matchedProducts.get(0);
        if (product == null) {
            return null;
        }
        return orderService.buildDraft(user, product.id(), requestedQuantity, threadId).draftJson();
    }

    private String composeSummary(String current, String userMessage, String answer) {
        var base = current == null || current.isBlank() ? "" : current + " | ";
        return base + "U:" + trim(userMessage, 80) + " A:" + trim(answer, 80);
    }

    private void markSessionEscalated(AssistantSession session) {
        session.setServiceStatus(SERVICE_STATUS_ESCALATED);
        session.setLastIntent("handoff");
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

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String detectIntent(String message) {
        var text = message == null ? "" : message.toLowerCase();
        if (isHumanSupportIntent(message)) {
            return "handoff";
        }
        if (text.contains("退款") || text.contains("退货") || text.contains("售后") || text.contains("保修")) {
            return "after_sales";
        }
        if (text.contains("地址") && (text.contains("修改") || text.contains("更改") || text.contains("更新") || text.contains("改成") || text.contains("改为") || text.contains("收货"))) {
            return "profile";
        }
        if (text.contains("下单") || text.contains("买") || text.contains("结算") || text.contains("订单") || text.contains("物流") || text.contains("发货")) {
            return "order";
        }
        if (text.contains("推荐") || text.contains("商品") || text.contains("手机") || text.contains("耳机") || text.contains("平板")) {
            return "product";
        }
        if (text.contains("faq") || text.contains("规则")) {
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
        return text.contains("订单")
                || text.contains("物流")
                || text.contains("发货")
                || text.contains("状态")
                || text.contains("到哪")
                || text.contains("快递")
                || text.contains("运单");
    }

    private boolean isCancelIntent(String message) {
        String text = message == null ? "" : message.toLowerCase();
        return text.contains("取消订单") || (text.contains("取消") && text.contains("订单"));
    }

    private boolean isConfirmReceiptIntent(String message) {
        String text = message == null ? "" : message.toLowerCase();
        return text.contains("确认收货") || (text.contains("收货") && text.contains("确认"));
    }

    private boolean isRefundIntent(String message) {
        String text = message == null ? "" : message.toLowerCase();
        return text.contains("退款") || text.contains("退货") || text.contains("售后");
    }

    private boolean isAddressIntent(String message) {
        String text = message == null ? "" : message.toLowerCase();
        return text.contains("地址")
                && (text.contains("修改")
                || text.contains("更改")
                || text.contains("更新")
                || text.contains("改成")
                || text.contains("改为")
                || text.contains("收货"));
    }

    private boolean isKnowledgePolicyIntent(String message) {
        String text = message == null ? "" : message.toLowerCase();
        return text.contains("规则") || text.contains("政策") || text.contains("说明") || text.contains("faq");
    }

    private boolean isHumanSupportIntent(String message) {
        String text = message == null ? "" : message.toLowerCase();
        boolean mentionsHumanSupport = text.contains("转人工")
                || text.contains("转接人工")
                || text.contains("人工客服")
                || text.contains("人工处理")
                || text.contains("人工跟进")
                || text.contains("真人客服")
                || text.contains("人工服务")
                || text.contains("联系客服")
                || text.contains("客服介入");
        boolean asksHow = text.contains("怎么") || text.contains("如何");
        return mentionsHumanSupport && !asksHow;
    }

    private boolean wantsDirectExecution(String message) {
        String text = message == null ? "" : message.toLowerCase();
        boolean asksHow = text.contains("怎么") || text.contains("如何") || text.contains("能不能") || text.contains("可以吗") || text.contains("可不可以");
        boolean asksInfo = text.contains("是什么") || text.contains("规则") || text.contains("政策") || text.contains("支持吗");
        boolean explicitAction = text.contains("帮我")
                || text.contains("给我")
                || text.contains("直接")
                || text.contains("现在")
                || text.contains("马上")
                || text.contains("立刻")
                || text.contains("提交")
                || text.contains("申请一下")
                || text.contains("取消一下")
                || text.contains("确认一下")
                || text.contains("改成")
                || text.contains("改为")
                || text.contains("修改为")
                || text.contains("修改成");
        return explicitAction && !asksHow && !asksInfo;
    }

    private int extractRequestedQuantity(String message) {
        Matcher matcher = PURCHASE_QUANTITY_PATTERN.matcher(message == null ? "" : message);
        if (!matcher.find()) {
            return 1;
        }
        int quantity = parseQuantityToken(matcher.group(1));
        return Math.max(1, Math.min(quantity, 20));
    }

    private int parseQuantityToken(String token) {
        if (token == null || token.isBlank()) {
            return 1;
        }
        return switch (token.trim()) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> {
                try {
                    yield Integer.parseInt(token.trim());
                } catch (NumberFormatException ex) {
                    yield 1;
                }
            }
        };
    }

    private boolean hasExplicitOrderReference(String message) {
        String text = message == null ? "" : message.toLowerCase();
        return ORDER_NO_PATTERN.matcher(text.toUpperCase()).find()
                || text.contains("这个订单")
                || text.contains("我的订单")
                || text.contains("最近订单")
                || text.contains("最新订单");
    }

    private List<OrderResponse> eligibleOrders(List<OrderResponse> orders, Predicate<OrderResponse> predicate) {
        return orders.stream().filter(predicate).toList();
    }

    private OrderResponse resolveDirectActionOrder(String message,
                                                   List<OrderResponse> orders,
                                                   Predicate<OrderResponse> predicate) {
        List<OrderResponse> eligible = eligibleOrders(orders, predicate);
        if (eligible.isEmpty()) {
            return null;
        }
        String text = message == null ? "" : message.toLowerCase();
        if (eligible.size() == 1) {
            return eligible.get(0);
        }
        if (text.contains("最近") || text.contains("最新")) {
            return eligible.get(0);
        }
        if (text.contains("这个订单") || text.contains("这笔订单") || text.contains("该订单")) {
            return eligible.size() == 1 ? eligible.get(0) : null;
        }
        return null;
    }

    private String buildActionOrderPrompt(String actionLabel, List<OrderResponse> eligibleOrders, String emptyMessage) {
        if (eligibleOrders.isEmpty()) {
            return emptyMessage;
        }
        return "我可以直接帮你%s，但你当前有多笔符合条件的订单。请补充订单号，例如：%s。"
                .formatted(actionLabel, eligibleOrders.stream()
                        .limit(3)
                        .map(OrderResponse::orderNo)
                        .reduce((left, right) -> left + " / " + right)
                        .orElse("ORD-XXXXXXXX"));
    }

    private String extractActionNote(String message, String defaultNote) {
        Matcher matcher = ACTION_NOTE_PATTERN.matcher(message == null ? "" : message);
        if (!matcher.find()) {
            return defaultNote;
        }
        String note = matcher.group("note");
        return sanitizeTailText(note, defaultNote);
    }

    private String extractNewShippingAddress(String message) {
        Matcher matcher = ADDRESS_UPDATE_PATTERN.matcher(message == null ? "" : message);
        if (!matcher.find()) {
            return null;
        }
        return sanitizeTailText(matcher.group("address"), null);
    }

    private String sanitizeTailText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim()
                .replace("。", "")
                .replace("！", "")
                .replace("!", "")
                .replace("？", "")
                .replace("?", "")
                .replace("；", "")
                .replace(";", "");
        return normalized.isBlank() ? fallback : normalized;
    }

    private OrderResponse findMentionedOrder(String message, List<OrderResponse> orders, OrderResponse exactOrder) {
        if (exactOrder != null) {
            return exactOrder;
        }
        if (orders == null || orders.isEmpty()) {
            return null;
        }
        String text = message == null ? "" : message.toLowerCase();
        return orders.stream()
                .filter(order -> order.orderNo() != null && text.contains(order.orderNo().toLowerCase()))
                .findFirst()
                .orElse(orders.get(0));
    }

    private OrderResponse findExactMentionedOrder(String message, List<OrderResponse> orders) {
        Matcher matcher = ORDER_NO_PATTERN.matcher(message == null ? "" : message.toUpperCase());
        if (!matcher.find()) {
            return null;
        }
        String orderNo = matcher.group();
        return orders.stream()
                .filter(order -> orderNo.equalsIgnoreCase(order.orderNo()))
                .findFirst()
                .orElse(null);
    }

    private String buildCancelGuide(OrderResponse focusOrder, List<OrderResponse> orders) {
        if (orders.isEmpty()) {
            return "你还没有可取消的订单。先下单后，我可以继续帮你判断是否能取消。";
        }
        if (focusOrder != null && canCancel(focusOrder)) {
            return "订单 %s 当前状态是 %s，可以在线取消。你可以在“我的订单”里点击“取消订单”，补充原因后提交。"
                    .formatted(focusOrder.orderNo(), statusLabel(focusOrder.status()));
        }
        if (focusOrder != null) {
            return "订单 %s 当前状态是 %s，暂不支持在线取消。通常只有待发货或处理中订单可以取消。"
                    .formatted(focusOrder.orderNo(), statusLabel(focusOrder.status()));
        }
        OrderResponse candidate = firstOrderWithStatus(orders, "CONFIRMED", "PROCESSING");
        if (candidate != null) {
            return "你最近可取消的订单是 %s，当前状态是 %s。到“我的订单”里点击“取消订单”就可以提交。"
                    .formatted(candidate.orderNo(), statusLabel(candidate.status()));
        }
        return "你最近的订单里没有支持在线取消的订单。通常只有待发货或处理中订单可以取消。";
    }

    private String buildConfirmReceiptGuide(OrderResponse focusOrder, List<OrderResponse> orders) {
        if (orders.isEmpty()) {
            return "你当前还没有订单，暂时不需要确认收货。";
        }
        if (focusOrder != null && canConfirmReceipt(focusOrder)) {
            return "订单 %s 已经是 %s，你可以在“我的订单”里点击“确认收货”完成订单。"
                    .formatted(focusOrder.orderNo(), statusLabel(focusOrder.status()));
        }
        if (focusOrder != null) {
            return "订单 %s 当前状态是 %s，只有已发货订单才能确认收货。"
                    .formatted(focusOrder.orderNo(), statusLabel(focusOrder.status()));
        }
        OrderResponse candidate = firstOrderWithStatus(orders, "SHIPPED");
        if (candidate != null) {
            return "你最近可以确认收货的订单是 %s，当前状态是 %s。收到商品后，直接在订单卡片里点击“确认收货”。"
                    .formatted(candidate.orderNo(), statusLabel(candidate.status()));
        }
        return "你最近没有处于已发货状态的订单，所以暂时不能确认收货。";
    }

    private String buildRefundGuide(OrderResponse focusOrder, List<OrderResponse> orders, List<String> sources) {
        String policySnippet = sources.isEmpty() ? "" : " 相关规则参考：" + summarizeSources(sources);
        if (orders.isEmpty()) {
            return "你当前还没有订单，暂时没有可以申请退款的对象。" + policySnippet;
        }
        if (focusOrder != null && canRefund(focusOrder)) {
            return "订单 %s 当前状态是 %s，可以在线申请退款。你可以在“我的订单”里点击“申请退款”，填写原因后提交。%s"
                    .formatted(focusOrder.orderNo(), statusLabel(focusOrder.status()), policySnippet.isBlank() ? "" : policySnippet);
        }
        if (focusOrder != null && "REFUND_REQUESTED".equals(focusOrder.status())) {
            return "订单 %s 已经进入退款申请状态，平台会继续审核处理。%s"
                    .formatted(focusOrder.orderNo(), policySnippet.isBlank() ? "" : policySnippet);
        }
        if (focusOrder != null && "REFUNDED".equals(focusOrder.status())) {
            return "订单 %s 已经退款完成，当前状态是 %s。你可以查看订单备注了解售后处理记录。%s"
                    .formatted(focusOrder.orderNo(), statusLabel(focusOrder.status()), policySnippet.isBlank() ? "" : policySnippet);
        }
        if (focusOrder != null) {
            return "订单 %s 当前状态是 %s，暂不支持在线申请退款。通常已发货或已完成订单支持发起退款。%s"
                    .formatted(focusOrder.orderNo(), statusLabel(focusOrder.status()), policySnippet.isBlank() ? "" : policySnippet);
        }
        OrderResponse candidate = firstOrderWithStatus(orders, "SHIPPED", "COMPLETED");
        if (candidate != null) {
            return "你最近可申请退款的订单是 %s，当前状态是 %s。到订单卡片里点击“申请退款”并补充原因即可。%s"
                    .formatted(candidate.orderNo(), statusLabel(candidate.status()), policySnippet.isBlank() ? "" : policySnippet);
        }
        return "你最近没有符合退款条件的订单。通常已发货或已完成订单支持在线申请退款。%s"
                .formatted(policySnippet.isBlank() ? "" : policySnippet);
    }

    private String buildAddressGuide(AppUser user, OrderResponse focusOrder) {
        String currentAddress = user.getShippingAddress() == null || user.getShippingAddress().isBlank()
                ? "你当前还没有设置默认收货地址"
                : "你当前的默认收货地址是：" + user.getShippingAddress();
        if (focusOrder == null) {
            return currentAddress + "。你可以在客户端“我的资料”里直接修改，保存后购物车结算会优先带出新地址。";
        }
        if (canUpdateShippingAddress(focusOrder)) {
            return currentAddress + "。订单 %s 当前状态是 %s，发货前还支持直接改这个订单的收货地址。你可以在“我的订单”里填写新地址后点击“修改地址”。"
                    .formatted(focusOrder.orderNo(), statusLabel(focusOrder.status()));
        }
        if ("SHIPPED".equals(focusOrder.status()) || "COMPLETED".equals(focusOrder.status()) || "REFUND_REQUESTED".equals(focusOrder.status())) {
            return currentAddress + "。订单 %s 当前状态是 %s，这类订单通常已经不支持在线改址；你仍然可以先更新默认地址，后续订单会使用新地址。"
                    .formatted(focusOrder.orderNo(), statusLabel(focusOrder.status()));
        }
        return currentAddress + "。如果你是想修改后续下单地址，直接在“我的资料”里保存即可；如果你想处理订单 %s，当前状态是 %s，建议优先联系平台人工处理。"
                .formatted(focusOrder.orderNo(), statusLabel(focusOrder.status()));
    }

    private String buildKnowledgeAnswer(List<String> sources) {
        return "我在知识库里查到了这些规则：" + summarizeSources(sources);
    }

    private String summarizeSources(List<String> sources) {
        return sources.stream()
                .limit(2)
                .map(source -> source.length() <= 96 ? source : source.substring(0, 96) + "...")
                .reduce((left, right) -> left + " | " + right)
                .orElse("暂无命中");
    }

    private OrderResponse firstOrderWithStatus(List<OrderResponse> orders, String... statuses) {
        for (OrderResponse order : orders) {
            for (String status : statuses) {
                if (status.equals(order.status())) {
                    return order;
                }
            }
        }
        return null;
    }

    private boolean canCancel(OrderResponse order) {
        return "CONFIRMED".equals(order.status()) || "PROCESSING".equals(order.status());
    }

    private boolean canConfirmReceipt(OrderResponse order) {
        return "SHIPPED".equals(order.status());
    }

    private boolean canRefund(OrderResponse order) {
        return "SHIPPED".equals(order.status()) || "COMPLETED".equals(order.status());
    }

    private boolean canUpdateShippingAddress(OrderResponse order) {
        return "CONFIRMED".equals(order.status()) || "PROCESSING".equals(order.status());
    }

    private String formatOrderDetail(OrderResponse order) {
        StringBuilder builder = new StringBuilder();
        builder.append(order.orderNo())
                .append("[")
                .append(statusLabel(order.status()))
                .append(", 金额")
                .append(order.totalAmount())
                .append(", 地址")
                .append(order.shippingAddress() == null ? "待补充" : order.shippingAddress());
        if (order.shippingCarrier() != null && !order.shippingCarrier().isBlank()) {
            builder.append(", 物流").append(order.shippingCarrier());
        }
        if (order.trackingNo() != null && !order.trackingNo().isBlank()) {
            builder.append(", 运单").append(order.trackingNo());
        }
        if (order.shippedAt() != null) {
            builder.append(", 发货时间").append(order.shippedAt());
        }
        builder.append("]");
        return builder.toString();
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "PENDING_CONFIRMATION" -> "待确认";
            case "CONFIRMED" -> "待发货";
            case "PROCESSING" -> "处理中";
            case "SHIPPED" -> "已发货";
            case "COMPLETED" -> "已完成";
            case "REFUND_REQUESTED" -> "退款处理中";
            case "REFUNDED" -> "已退款";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
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

    private record ActionExecution(String answer, String intent) {}
}

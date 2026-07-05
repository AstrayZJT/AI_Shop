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
        var sources = knowledgeService.search(message).stream()
                .map(s -> s.title() + ": " + s.chunkText())
                .limit(3)
                .toList();
        log.info("assistant context: sessionId={}, intent={}, sourceCount={}",
                session.getId(), intent, sources.size());

        var answer = buildAnswer(user, intent, sources, message, currentThreadId);
        var draftJson = buildDraftIfNeeded(user, intent, currentThreadId);
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

    private String buildAnswer(AppUser user, String intent, List<String> sources, String message, String threadId) {
        if ("order".equals(intent)) {
            var products = productService.listAll();
            if (!products.isEmpty()) {
                var product = products.get(0);
                return "我已经为你准备了下单草稿，默认推荐商品是：%s，价格 %s。确认后我再帮你真正创建订单。"
                        .formatted(product.name(), product.price());
            }
        }
        if ("product".equals(intent)) {
            var products = productService.listAll();
            if (!products.isEmpty()) {
                return "我先给你推荐这款商品：%s，价格 %s。".formatted(products.get(0).name(), products.get(0).price());
            }
        }

        var prompt = """
                你是一个电商智能助手。
                用户：%s
                当前对话：%s
                用户消息：%s
                意图：%s
                相关知识：%s
                回答要求：优先结合商品、订单和知识库，不要编造；回答要简洁可用。
                """.formatted(
                user.getDisplayName(),
                threadId,
                message,
                intent,
                sources.isEmpty() ? "无" : String.join(" | ", sources));

        try {
            return chatModel.chat(prompt);
        } catch (Exception ex) {
            return "我收到了你的问题，正在整理更合适的建议。";
        }
    }

    private String buildDraftIfNeeded(AppUser user, String intent, String threadId) {
        if (!"order".equals(intent)) {
            return null;
        }
        var products = productService.listAll();
        if (products.isEmpty()) {
            return null;
        }
        return orderService.buildDraft(user, products.get(0).id(), 1, threadId).draftJson();
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
        if (text.contains("下单") || text.contains("买") || text.contains("结算")) {
            return "order";
        }
        if (text.contains("推荐") || text.contains("商品")) {
            return "product";
        }
        if (text.contains("faq") || text.contains("售后") || text.contains("规则") || text.contains("退款")) {
            return "rag";
        }
        return "chat";
    }
}

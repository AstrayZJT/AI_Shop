package com.aishop.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.assistant.application.AssistantAgentService;
import com.aishop.assistant.application.AgentTrace;
import com.aishop.assistant.context.AssistantContextBuilder;
import com.aishop.domain.AppUser;
import com.aishop.domain.AssistantMessage;
import com.aishop.domain.AssistantSession;
import com.aishop.dto.AssistantDtos.ChatResponse;
import com.aishop.dto.AssistantDtos.KnowledgeSourceResponse;
import com.aishop.dto.KnowledgeDtos.SearchResponse;
import com.aishop.repository.AssistantMessageRepository;
import com.aishop.repository.AssistantSessionRepository;

@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final String SERVICE_STATUS_ACTIVE = "ACTIVE";
    private static final String SERVICE_STATUS_ESCALATED = "ESCALATED";
    private static final String SERVICE_STATUS_RESOLVED = "RESOLVED";

    private final AssistantSessionRepository sessionRepository;
    private final AssistantMessageRepository messageRepository;
    private final CompiledGraph<MessagesState<String>> assistantGraph;
    private final AssistantAgentService assistantAgentService;
    private final AssistantContextBuilder contextBuilder;

    public AssistantService(AssistantSessionRepository sessionRepository,
                            AssistantMessageRepository messageRepository,
                            CompiledGraph<MessagesState<String>> assistantGraph,
                            AssistantAgentService assistantAgentService,
                            AssistantContextBuilder contextBuilder) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.assistantGraph = assistantGraph;
        this.assistantAgentService = assistantAgentService;
        this.contextBuilder = contextBuilder;
    }

    public List<AssistantSession> listSessions(AppUser user) {
        return sessionRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public AssistantSession getOrCreateSession(AppUser user, Long sessionId) {
        if (sessionId != null) {
            AssistantSession session = sessionRepository.findByIdAndUser(sessionId, user)
                    .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
            return normalizeServiceStatus(session);
        }
        AssistantSession session = new AssistantSession();
        session.setUser(user);
        session.setTitle("智能助手会话");
        session.setSummary("新会话");
        session.setLastIntent("unknown");
        session.setServiceStatus(SERVICE_STATUS_ACTIVE);
        session.setSupportUnreadCount(0L);
        session.setCustomerUnreadCount(0L);
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
        String requestText = normalizedNote == null ? "申请人工客服" : "申请人工客服：" + normalizedNote;
        markSessionEscalated(session);
        saveMessage(session, "user", requestText);
        String answer = alreadyEscalated
                ? "你的会话已经在人工跟进中，我把刚补充的说明也同步给人工客服了。"
                : "我已经帮你转接人工客服，接下来管理员可以在后台直接回复你。";
        session.setSummary(contextBuilder.nextConversationSummary(session.getSummary(), requestText, answer));
        sessionRepository.save(session);
        saveMessage(session, "assistant", answer);
        return session;
    }

    @Transactional
    public ChatResponse chat(AppUser user, Long sessionId, String message, String threadId) {
        if (user == null) {
            throw new IllegalArgumentException("请先登录");
        }
        AssistantSession session = getOrCreateSession(user, sessionId);
        reopenResolvedSession(session);
        String currentThreadId = threadId == null || threadId.isBlank()
                ? "assistant-" + session.getId()
                : threadId;
        log.info("assistant request: user={}, sessionId={}, threadId={}, message={}",
                user.getUsername(), session.getId(), currentThreadId, preview(message));

        invokeCheckpointGraph(message, currentThreadId);

        String answer;
        String intent;
        List<KnowledgeSourceResponse> sources;
        AgentTrace trace = AgentTrace.empty();
        if (SERVICE_STATUS_ESCALATED.equals(normalizeServiceStatus(session.getServiceStatus()))) {
            answer = "你的会话已经转给人工客服，我会把这条补充信息同步给人工同事，请稍候查看人工回复。";
            intent = "handoff";
            sources = List.of();
        } else {
            var turn = assistantAgentService.handle(user, session, message);
            trace = turn.trace();
            var primaryTask = turn.execution().planner().plan().tasks().getFirst();
            answer = turn.composedAnswer().answer();
            intent = primaryTask.intent().name().toLowerCase();
            sources = turn.ragAnswer() == null
                    ? List.of()
                    : turn.ragAnswer().retrievalHits().stream().map(this::toKnowledgeSourceResponse).toList();
            log.info(
                    "assistant agent response: traceId={}, sessionId={}, intent={}, plannerSource={}, taskCount={}, planRunId={}, runStatus={}, resumed={}, totalLatencyMs={}, inputTokens={}, outputTokens={}, contextCharacters={}, contextTruncated={}, answerMode={}, sourceCount={}",
                    trace.traceId(), session.getId(), intent, turn.execution().planner().source(),
                    turn.execution().taskResults().size(), turn.workflow().planRunId(),
                    turn.workflow().status(), turn.workflow().resumed(), trace.totalLatencyMs(),
                    trace.inputTokens(), trace.outputTokens(), turn.context().estimatedCharacters(),
                    turn.context().truncated(), turn.composedAnswer().mode(), sources.size());
        }

        session.setLastIntent(intent);
        session.setSummary(contextBuilder.nextConversationSummary(session.getSummary(), message, answer));
        sessionRepository.save(session);
        saveMessage(session, "user", message);
        saveMessage(session, "assistant", answer);

        return new ChatResponse(
                session.getId(), answer, intent, currentThreadId, sources, null, List.of(), trace);
    }

    @Transactional
    public List<AssistantMessage> messages(Long sessionId, AppUser user) {
        if (user == null) {
            throw new IllegalArgumentException("请先登录");
        }
        AssistantSession session = sessionRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (safeUnreadCount(session.getCustomerUnreadCount()) > 0L) {
            session.setCustomerUnreadCount(0L);
            sessionRepository.save(session);
        }
        return messageRepository.findBySessionOrderByCreatedAtAsc(session);
    }

    private void invokeCheckpointGraph(String message, String threadId) {
        try {
            assistantGraph.invoke(Map.of("messages", List.of(message)), RunnableConfig.builder()
                    .threadId(threadId)
                    .graphId("assistant-workflow")
                    .build());
        } catch (Exception ex) {
            log.warn("assistant graph execution failed, continuing without checkpointed graph state", ex);
        }
    }

    private void reopenResolvedSession(AssistantSession session) {
        if (SERVICE_STATUS_RESOLVED.equals(normalizeServiceStatus(session.getServiceStatus()))) {
            session.setServiceStatus(SERVICE_STATUS_ACTIVE);
            session.setResolvedAt(null);
        }
    }

    private AssistantSession normalizeServiceStatus(AssistantSession session) {
        String normalized = normalizeServiceStatus(session.getServiceStatus());
        boolean changed = false;
        if (!normalized.equals(session.getServiceStatus())) {
            session.setServiceStatus(normalized);
            changed = true;
        }
        if (session.getSupportUnreadCount() == null) {
            session.setSupportUnreadCount(0L);
            changed = true;
        }
        if (session.getCustomerUnreadCount() == null) {
            session.setCustomerUnreadCount(0L);
            changed = true;
        }
        return changed ? sessionRepository.save(session) : session;
    }

    private String normalizeServiceStatus(String status) {
        return status == null || status.isBlank() ? SERVICE_STATUS_ACTIVE : status.trim().toUpperCase();
    }

    private void markSessionEscalated(AssistantSession session) {
        session.setServiceStatus(SERVICE_STATUS_ESCALATED);
        session.setLastIntent("handoff");
        session.setFirstSupportReplyAt(null);
        session.setResolvedAt(null);
    }

    private void saveMessage(AssistantSession session, String role, String content) {
        AssistantMessage message = new AssistantMessage();
        message.setSession(session);
        message.setRole(role);
        message.setContent(content);
        AssistantMessage saved = messageRepository.save(message);
        updateSessionActivity(session, role, saved.getCreatedAt());
    }

    private void updateSessionActivity(AssistantSession session, String role, Instant messageTime) {
        boolean changed = false;
        if ("user".equals(role)) {
            session.setLastCustomerMessageAt(messageTime);
            session.setCustomerUnreadCount(0L);
            if (SERVICE_STATUS_ESCALATED.equals(normalizeServiceStatus(session.getServiceStatus()))) {
                session.setSupportUnreadCount(safeUnreadCount(session.getSupportUnreadCount()) + 1L);
            }
            changed = true;
        } else if ("support".equals(role)) {
            session.setLastSupportMessageAt(messageTime);
            session.setSupportUnreadCount(0L);
            session.setCustomerUnreadCount(safeUnreadCount(session.getCustomerUnreadCount()) + 1L);
            if (session.getFirstSupportReplyAt() == null) {
                session.setFirstSupportReplyAt(messageTime);
            }
            changed = true;
        }
        if (changed) {
            sessionRepository.save(session);
        }
    }

    private KnowledgeSourceResponse toKnowledgeSourceResponse(SearchResponse response) {
        return new KnowledgeSourceResponse(
                response.id(), response.documentId(), response.title(), response.chunkText(),
                response.matchMode(), response.score(), response.matchedTerms(), response.indexed());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private long safeUnreadCount(Long count) {
        return count == null ? 0L : count;
    }

    private String preview(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }
}

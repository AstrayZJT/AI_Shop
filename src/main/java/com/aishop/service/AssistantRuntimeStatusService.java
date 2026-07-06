package com.aishop.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.config.RagProperties;
import com.aishop.config.ShopProperties;
import com.aishop.dto.AssistantDtos.RuntimeHealthResponse;
import com.aishop.repository.KnowledgeChunkRepository;
import com.aishop.repository.KnowledgeDocumentRepository;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;

@Service
public class AssistantRuntimeStatusService {

    private final ShopProperties shopProperties;
    private final RagProperties ragProperties;
    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStoreFacade embeddingStoreFacade;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeChunkRepository knowledgeChunkRepository;

    public AssistantRuntimeStatusService(ShopProperties shopProperties,
                                         RagProperties ragProperties,
                                         ChatModel chatModel,
                                         EmbeddingModel embeddingModel,
                                         EmbeddingStoreFacade embeddingStoreFacade,
                                         KnowledgeDocumentRepository knowledgeDocumentRepository,
                                         KnowledgeChunkRepository knowledgeChunkRepository) {
        this.shopProperties = shopProperties;
        this.ragProperties = ragProperties;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStoreFacade = embeddingStoreFacade;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeChunkRepository = knowledgeChunkRepository;
    }

    @Transactional(readOnly = true)
    public RuntimeHealthResponse runtimeHealth() {
        List<String> warnings = new ArrayList<>();
        boolean aiEnabled = shopProperties.ai().enabled();
        boolean apiKeyConfigured = hasText(shopProperties.ai().apiKey());
        boolean requestReady = !aiEnabled || apiKeyConfigured;
        String mode = aiEnabled ? "REMOTE_MODEL" : "LOCAL_FALLBACK";
        String provider = inferProvider(aiEnabled, shopProperties.ai().baseUrl());
        String vectorStoreType = embeddingStoreFacade instanceof PgVectorEmbeddingStoreFacade ? "PGVECTOR" : "IN_MEMORY";
        boolean vectorStorePersistent = "PGVECTOR".equals(vectorStoreType);
        String activeChatModelName = aiEnabled
                ? blankToNull(shopProperties.ai().modelName())
                : "LOCAL_FALLBACK_CHAT";
        String activeEmbeddingModelName = aiEnabled
                ? blankToNull(shopProperties.ai().embeddingModelName())
                : "LOCAL_FAKE_EMBEDDING_64D";

        if (!aiEnabled) {
            warnings.add("当前是本地联调模式，聊天回复和 embedding 走本地兜底逻辑，不代表真实大模型效果。");
        }
        if (aiEnabled && !apiKeyConfigured) {
            warnings.add("当前进程已启用真实 AI 模式，但没有读取到 OPENAI_API_KEY，真实模型请求会失败。");
        }
        if (aiEnabled && !vectorStorePersistent) {
            warnings.add("当前向量检索没有落到 pgvector，而是在使用内存向量存储。");
        }

        long indexedSegmentCount = safeSegmentCount(warnings);
        long documentCount = knowledgeDocumentRepository.count();
        long chunkCount = knowledgeChunkRepository.count();
        if (documentCount > 0 && indexedSegmentCount == 0) {
            warnings.add("知识文档已存在，但当前向量索引为空，请检查 embedding / pgvector 配置或重新导入知识库。");
        }

        return new RuntimeHealthResponse(
                aiEnabled,
                mode,
                provider,
                requestReady,
                apiKeyConfigured,
                aiEnabled ? blankToNull(shopProperties.ai().baseUrl()) : null,
                activeChatModelName,
                activeEmbeddingModelName,
                beanLabel(chatModel),
                beanLabel(embeddingModel),
                vectorStoreType,
                vectorStorePersistent,
                ragProperties.pgvector().table(),
                ragProperties.knowledgePath(),
                shopProperties.rag().topK(),
                documentCount,
                chunkCount,
                indexedSegmentCount,
                List.copyOf(warnings));
    }

    private long safeSegmentCount(List<String> warnings) {
        try {
            return embeddingStoreFacade.segmentCount();
        } catch (Exception ex) {
            warnings.add("向量索引状态暂时无法读取: " + ex.getMessage());
            return 0L;
        }
    }

    private String inferProvider(boolean aiEnabled, String baseUrl) {
        if (!aiEnabled) {
            return "LOCAL_RULES";
        }
        String normalized = baseUrl == null ? "" : baseUrl.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("dashscope.aliyuncs.com")) {
            return "DASHSCOPE_COMPATIBLE";
        }
        if (normalized.contains("api.openai.com")) {
            return "OPENAI";
        }
        return "OPENAI_COMPATIBLE";
    }

    private String beanLabel(Object bean) {
        Class<?> type = bean.getClass();
        String simpleName = type.getSimpleName();
        return simpleName == null || simpleName.isBlank() ? type.getName() : simpleName;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}

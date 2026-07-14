package com.aishop.assistant.tool.tools;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.tool.AssistantTool;
import com.aishop.assistant.tool.PreparedToolCall;
import com.aishop.assistant.tool.ToolArguments;
import com.aishop.assistant.tool.ToolContext;
import com.aishop.assistant.tool.ToolExecutionOutcome;
import com.aishop.assistant.tool.ToolPolicy;
import com.aishop.assistant.tool.ToolRiskLevel;
import com.aishop.service.KnowledgeService;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

@Component
public class SearchKnowledgeTool implements AssistantTool {

    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("query");
    private static final ToolPolicy POLICY = new ToolPolicy(
            "search_knowledge",
            "从平台知识库检索退款、物流、发票、售后等规则，并返回可引用的原文片段。",
            AssistantAction.SEARCH_KNOWLEDGE,
            ToolRiskLevel.READ_ONLY,
            true);
    private static final ToolSpecification SPECIFICATION = ToolSpecification.builder()
            .name(POLICY.name())
            .description(POLICY.description())
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("query", "需要检索的规则或知识问题")
                    .required("query")
                    .additionalProperties(false)
                    .build())
            .strict(true)
            .build();

    private final KnowledgeService knowledgeService;

    public SearchKnowledgeTool(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public ToolPolicy policy() {
        return POLICY;
    }

    @Override
    public ToolSpecification specification() {
        return SPECIFICATION;
    }

    @Override
    public PreparedToolCall prepare(ToolContext context, Map<String, Object> arguments) {
        ToolArguments.rejectUnknown(arguments, ALLOWED_ARGUMENTS);
        String query = ToolArguments.requireString(arguments, "query", 512);
        return new PreparedToolCall(
                POLICY.action(), POLICY.name(), POLICY.riskLevel(), Map.of("query", query), null, Map.of());
    }

    @Override
    public ToolExecutionOutcome execute(ToolContext context, PreparedToolCall call) {
        String query = (String) call.arguments().get("query");
        List<KnowledgeSource> sources = knowledgeService.search(query).stream()
                .limit(5)
                .map(hit -> new KnowledgeSource(
                        hit.id(),
                        hit.documentId(),
                        hit.title(),
                        hit.chunkText(),
                        hit.matchMode(),
                        hit.score()))
                .toList();
        return new ToolExecutionOutcome(
                Map.of("sources", sources, "count", sources.size()),
                sources.isEmpty() ? "知识库没有可靠命中" : "知识库命中 " + sources.size() + " 条内容");
    }

    public record KnowledgeSource(
            Long chunkId,
            Long documentId,
            String title,
            String text,
            String matchMode,
            Double score
    ) {
    }
}

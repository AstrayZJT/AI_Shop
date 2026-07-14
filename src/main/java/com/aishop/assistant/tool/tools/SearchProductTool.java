package com.aishop.assistant.tool.tools;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
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
import com.aishop.service.ProductService;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

@Component
public class SearchProductTool implements AssistantTool {

    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("query", "category", "budgetMin", "budgetMax");
    private static final ToolPolicy POLICY = new ToolPolicy(
            "search_products",
            "按关键词、分类和预算搜索在售商品。只读取商品信息，不创建订单。",
            AssistantAction.SEARCH_PRODUCT,
            ToolRiskLevel.READ_ONLY,
            true);
    private static final ToolSpecification SPECIFICATION = ToolSpecification.builder()
            .name(POLICY.name())
            .description(POLICY.description())
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("query", "商品关键词或用户需求，例如耳机、通勤降噪")
                    .addStringProperty("category", "可选平台一级分类；不确定真实分类时请省略，具体品类放入 query")
                    .addNumberProperty("budgetMin", "可选最低预算")
                    .addNumberProperty("budgetMax", "可选最高预算")
                    .required("query")
                    .additionalProperties(false)
                    .build())
            .strict(true)
            .build();

    private final ProductService productService;

    public SearchProductTool(ProductService productService) {
        this.productService = productService;
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
        String category = ToolArguments.optionalString(arguments, "category", 64);
        BigDecimal budgetMin = ToolArguments.optionalDecimal(arguments, "budgetMin");
        BigDecimal budgetMax = ToolArguments.optionalDecimal(arguments, "budgetMax");
        if (budgetMin != null && budgetMax != null && budgetMin.compareTo(budgetMax) > 0) {
            throw new IllegalArgumentException("budgetMin 不能大于 budgetMax");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("query", query);
        if (category != null) {
            normalized.put("category", category);
        }
        if (budgetMin != null) {
            normalized.put("budgetMin", budgetMin);
        }
        if (budgetMax != null) {
            normalized.put("budgetMax", budgetMax);
        }
        return new PreparedToolCall(
                POLICY.action(), POLICY.name(), POLICY.riskLevel(), normalized, null, Map.of());
    }

    @Override
    public ToolExecutionOutcome execute(ToolContext context, PreparedToolCall call) {
        String query = (String) call.arguments().get("query");
        String category = (String) call.arguments().get("category");
        BigDecimal budgetMin = (BigDecimal) call.arguments().get("budgetMin");
        BigDecimal budgetMax = (BigDecimal) call.arguments().get("budgetMax");

        var candidates = productService.search(query);
        boolean recognizedCategory = category != null && candidates.stream()
                .anyMatch(product -> product.category() != null && product.category().equalsIgnoreCase(category));
        List<ProductItem> products = candidates.stream()
                .filter(product -> product.stock() != null && product.stock() > 0)
                .filter(product -> !recognizedCategory
                        || product.category() != null && product.category().equalsIgnoreCase(category))
                .filter(product -> budgetMin == null || product.price().compareTo(budgetMin) >= 0)
                .filter(product -> budgetMax == null || product.price().compareTo(budgetMax) <= 0)
                .limit(5)
                .map(product -> new ProductItem(
                        product.id(),
                        product.sku(),
                        product.name(),
                        product.price(),
                        product.stock(),
                        product.category(),
                        product.averageRating(),
                        product.reviewCount()))
                .toList();
        return new ToolExecutionOutcome(
                Map.of("products", products, "count", products.size()),
                products.isEmpty() ? "没有找到符合条件的商品" : "找到 " + products.size() + " 个商品");
    }

    public record ProductItem(
            Long id,
            String sku,
            String name,
            BigDecimal price,
            Integer stock,
            String category,
            Double averageRating,
            Long reviewCount
    ) {
    }
}

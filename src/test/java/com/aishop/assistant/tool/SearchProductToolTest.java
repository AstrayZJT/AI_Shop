package com.aishop.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aishop.assistant.tool.tools.SearchProductTool;
import com.aishop.service.ProductService;

class SearchProductToolTest {

    @Test
    void filtersProductsByBudgetAndStock() {
        ProductService productService = mock(ProductService.class);
        when(productService.search("耳机")).thenReturn(List.of(
                ToolTestFixtures.product(1L, "入门耳机", "399", 10, "数码"),
                ToolTestFixtures.product(2L, "高端耳机", "899", 10, "数码"),
                ToolTestFixtures.product(3L, "缺货耳机", "299", 0, "数码")));
        SearchProductTool tool = new SearchProductTool(productService);

        PreparedToolCall call = tool.prepare(
                ToolTestFixtures.context(), Map.of("query", "耳机", "budgetMax", 500));
        ToolExecutionOutcome outcome = tool.execute(ToolTestFixtures.context(), call);

        assertThat(outcome.data()).containsEntry("count", 1);
        assertThat((List<?>) outcome.data().get("products")).hasSize(1);
    }

    @Test
    void rejectsUnknownProductArgument() {
        SearchProductTool tool = new SearchProductTool(mock(ProductService.class));

        assertThatThrownBy(() -> tool.prepare(
                ToolTestFixtures.context(), Map.of("query", "耳机", "maxPrice", 500)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxPrice");
    }

    @Test
    void rejectsInvertedBudgetRange() {
        SearchProductTool tool = new SearchProductTool(mock(ProductService.class));

        assertThatThrownBy(() -> tool.prepare(
                ToolTestFixtures.context(), Map.of("query", "耳机", "budgetMin", 1000, "budgetMax", 500)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("budgetMin");
    }

    @Test
    void treatsUnknownNaturalLanguageCategoryAsSoftHint() {
        ProductService productService = mock(ProductService.class);
        when(productService.search("耳机")).thenReturn(List.of(
                ToolTestFixtures.product(1L, "入门耳机", "399", 10, "数码")));
        SearchProductTool tool = new SearchProductTool(productService);

        PreparedToolCall call = tool.prepare(
                ToolTestFixtures.context(), Map.of("query", "耳机", "category", "耳机", "budgetMax", 500));
        ToolExecutionOutcome outcome = tool.execute(ToolTestFixtures.context(), call);

        assertThat(outcome.data()).containsEntry("count", 1);
    }
}

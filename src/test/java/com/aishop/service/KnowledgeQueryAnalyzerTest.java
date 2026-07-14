package com.aishop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class KnowledgeQueryAnalyzerTest {

    private final KnowledgeQueryAnalyzer analyzer = new KnowledgeQueryAnalyzer();

    @Test
    void extractsDomainTermsWithoutGeneratingGenericBigrams() {
        var result = analyzer.analyze("七天无理由退货怎么办？");

        assertThat(result.normalized()).isEqualTo("七天无理由退货怎么办？");
        assertThat(result.terms()).contains("七天无理由", "七天", "无理由", "退货");
        assertThat(result.terms()).doesNotContain("怎么办");
        assertThat(result.terms()).hasSizeLessThanOrEqualTo(10);
    }

    @Test
    void returnsNullForBlankAndRejectsOversizedQuery() {
        assertThat(analyzer.analyze("  ")).isNull();
        assertThat(analyzer.analyze("%_? ")).isNull();
        assertThatThrownBy(() -> analyzer.analyze("问".repeat(513)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("512");
    }
}

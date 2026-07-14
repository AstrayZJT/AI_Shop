package com.aishop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.aishop.config.RagProperties;

class KnowledgeTextProcessorTest {

    @Test
    void normalizesWhitespaceButPreservesOriginalContent() {
        String original = "  第一条   规则\r\n\r\n\t第二条规则  ";
        var processed = processor(200, 40).process(original);

        assertThat(processed.originalContent()).isEqualTo(original);
        assertThat(processed.normalizedContent()).isEqualTo("第一条 规则\n第二条规则");
        assertThat(processed.contentHash()).hasSize(64);
    }

    @Test
    void splitsLongTextWithConfiguredOverlapAndOffsets() {
        String text = "第一段规则。" + "甲".repeat(180) + "。第二段规则。" + "乙".repeat(180) + "。";
        var processed = processor(200, 50).process(text);

        assertThat(processed.chunks()).hasSizeGreaterThan(1);
        var first = processed.chunks().get(0);
        var second = processed.chunks().get(1);
        assertThat(second.startOffset()).isLessThan(first.endOffset());
        assertThat(first.text()).isEqualTo(processed.normalizedContent()
                .substring(first.startOffset(), first.endOffset()));
        assertThat(second.index()).isEqualTo(1);
    }

    @Test
    void prefersSentenceBoundaryBeforeHardLimit() {
        String text = "甲".repeat(140) + "。" + "乙".repeat(100);
        var first = processor(200, 20).process(text).chunks().get(0);

        assertThat(first.text()).endsWith("。");
        assertThat(first.text()).hasSize(141);
    }

    @Test
    void rejectsBlankDocument() {
        assertThatThrownBy(() -> processor(200, 20).process(" \r\n "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    private KnowledgeTextProcessor processor(int chunkSize, int overlap) {
        return new KnowledgeTextProcessor(new RagProperties(
                "knowledge", chunkSize, overlap, 4, 0.6, 0.62, 2000,
                new RagProperties.Pgvector(null, null, null, null, null, "knowledge_embeddings")));
    }
}

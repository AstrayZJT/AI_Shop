package com.aishop.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Component;

import com.aishop.config.RagProperties;

@Component
public class KnowledgeTextProcessor {

    private final RagProperties properties;

    public KnowledgeTextProcessor(RagProperties properties) {
        this.properties = properties;
    }

    public ProcessedDocument process(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("知识文档内容不能为空");
        }
        String normalized = normalize(content);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("知识文档清洗后没有有效内容");
        }
        return new ProcessedDocument(content, normalized, sha256(normalized), split(normalized));
    }

    String normalize(String content) {
        String lineNormalized = content.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder result = new StringBuilder(lineNormalized.length());
        boolean previousBlank = false;
        for (String line : lineNormalized.split("\n", -1)) {
            String cleaned = line.strip().replaceAll("[\\t\\x0B\\f ]+", " ");
            if (cleaned.isBlank()) {
                if (!previousBlank && !result.isEmpty()) {
                    result.append('\n');
                }
                previousBlank = true;
                continue;
            }
            if (!result.isEmpty() && result.charAt(result.length() - 1) != '\n') {
                result.append('\n');
            }
            result.append(cleaned);
            previousBlank = false;
        }
        return result.toString().strip();
    }

    private List<ProcessedChunk> split(String text) {
        int chunkSize = properties.chunkSize();
        int overlap = properties.chunkOverlap();
        List<ProcessedChunk> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;
        while (start < text.length()) {
            int proposedEnd = Math.min(text.length(), start + chunkSize);
            int end = proposedEnd == text.length() ? proposedEnd : findBoundary(text, start, proposedEnd);
            int actualStart = skipWhitespaceForward(text, start, end);
            int actualEnd = skipWhitespaceBackward(text, actualStart, end);
            if (actualEnd > actualStart) {
                String chunkText = text.substring(actualStart, actualEnd);
                chunks.add(new ProcessedChunk(
                        index++, actualStart, actualEnd, chunkText, sha256(chunkText)));
            }
            if (end >= text.length()) {
                break;
            }
            int nextStart = Math.max(start + 1, end - overlap);
            start = skipWhitespaceForward(text, nextStart, text.length());
        }
        return List.copyOf(chunks);
    }

    private int findBoundary(String text, int start, int proposedEnd) {
        int earliest = start + Math.max(1, (proposedEnd - start) / 2);
        for (int i = proposedEnd - 1; i >= earliest; i--) {
            char value = text.charAt(i);
            if (value == '\n' || value == '。' || value == '！' || value == '？'
                    || value == '；' || value == '.' || value == '!' || value == '?') {
                return i + 1;
            }
        }
        return proposedEnd;
    }

    private int skipWhitespaceForward(String text, int start, int limit) {
        int cursor = start;
        while (cursor < limit && Character.isWhitespace(text.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private int skipWhitespaceBackward(String text, int start, int end) {
        int cursor = end;
        while (cursor > start && Character.isWhitespace(text.charAt(cursor - 1))) {
            cursor--;
        }
        return cursor;
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", ex);
        }
    }

    public record ProcessedDocument(
            String originalContent,
            String normalizedContent,
            String contentHash,
            List<ProcessedChunk> chunks
    ) {
    }

    public record ProcessedChunk(
            int index,
            int startOffset,
            int endOffset,
            String text,
            String contentHash
    ) {
    }
}

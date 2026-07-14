package com.aishop.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class KnowledgeQueryAnalyzer {

    private static final List<String> DOMAIN_TERMS = List.of(
            "七天无理由", "七天", "无理由", "退货", "退款", "售后", "换货",
            "物流", "发货", "签收", "订单", "保修", "发票", "优惠", "地址", "支付");

    public AnalyzedQuery analyze(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String normalized = query.strip().replaceAll("\\s+", " ");
        if (normalized.length() > 512) {
            throw new IllegalArgumentException("知识检索问题不能超过 512 字符");
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        String semanticCharacters = lowered.replaceAll(
                "[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]", "");
        if (semanticCharacters.length() < 2) {
            return null;
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String token : lowered.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+")) {
            if (token.length() >= 2 && token.length() <= 32) {
                terms.add(token);
            }
        }
        for (String term : DOMAIN_TERMS) {
            if (lowered.contains(term)) {
                terms.add(term);
            }
        }
        List<String> limited = new ArrayList<>(terms);
        if (limited.size() > 10) {
            limited = limited.subList(0, 10);
        }
        return new AnalyzedQuery(normalized, List.copyOf(limited));
    }

    public record AnalyzedQuery(String normalized, List<String> terms) {
    }
}

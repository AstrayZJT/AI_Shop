package com.aishop.assistant.tool;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

public final class ToolArguments {

    private ToolArguments() {
    }

    public static void rejectUnknown(Map<String, Object> arguments, Set<String> allowed) {
        for (String key : arguments.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("工具参数不支持: " + key);
            }
        }
    }

    public static String requireString(Map<String, Object> arguments, String name, int maxLength) {
        String value = optionalString(arguments, name, maxLength);
        if (value == null) {
            throw new IllegalArgumentException("缺少工具参数: " + name);
        }
        return value;
    }

    public static String optionalString(Map<String, Object> arguments, String name, int maxLength) {
        Object raw = arguments.get(name);
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof String value)) {
            throw new IllegalArgumentException("工具参数 " + name + " 必须是字符串");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("工具参数 " + name + " 超过长度限制");
        }
        return normalized;
    }

    public static BigDecimal optionalDecimal(Map<String, Object> arguments, String name) {
        Object raw = arguments.get(name);
        if (raw == null) {
            return null;
        }
        try {
            BigDecimal value = raw instanceof Number number
                    ? new BigDecimal(number.toString())
                    : new BigDecimal(raw.toString().trim());
            if (value.signum() < 0) {
                throw new IllegalArgumentException("工具参数 " + name + " 不能为负数");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("工具参数 " + name + " 必须是数字", ex);
        }
    }
}

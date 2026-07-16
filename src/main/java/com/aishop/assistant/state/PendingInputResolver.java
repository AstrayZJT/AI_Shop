package com.aishop.assistant.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.aishop.assistant.model.AssistantTask;

@Component
public class PendingInputResolver {

    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("ORD-[A-Z0-9]{8}", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADDRESS_PATTERN = Pattern.compile(
            "(?:地址(?:是|为|改成|改为)?|改成|改为)[：:，,\\s]*(?<address>.+)$");

    public AssistantTask resolve(AssistantTask task, String userMessage) {
        Map<String, Object> slots = new LinkedHashMap<>(task.slots());
        List<String> missing = new ArrayList<>(task.missingSlots());
        String message = userMessage == null ? "" : userMessage.strip();

        if (missing.contains("orderNo")) {
            Matcher matcher = ORDER_NO_PATTERN.matcher(message);
            if (matcher.find()) {
                slots.put("orderNo", matcher.group().toUpperCase(Locale.ROOT));
                missing.remove("orderNo");
            }
        }
        if (missing.contains("address")) {
            Matcher matcher = ADDRESS_PATTERN.matcher(message);
            if (matcher.find() && !matcher.group("address").isBlank()) {
                slots.put("address", clip(matcher.group("address").strip(), 512));
                missing.remove("address");
            }
        }
        return new AssistantTask(
                task.taskId(), task.intent(), task.action(), task.executionMode(), slots,
                missing, task.dependsOn(), task.conditions(), task.confidence(), task.reason());
    }

    private String clip(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}

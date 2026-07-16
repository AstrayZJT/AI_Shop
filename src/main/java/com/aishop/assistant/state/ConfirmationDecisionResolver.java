package com.aishop.assistant.state;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class ConfirmationDecisionResolver {

    private static final Set<String> CONFIRMATIONS = Set.of(
            "确认", "确认执行", "确定", "同意", "继续执行", "是的", "好的确认");
    private static final Set<String> REJECTIONS = Set.of(
            "取消操作", "不执行", "拒绝", "算了", "不用了", "放弃操作");

    public ConfirmationDecision resolve(String message) {
        String normalized = message == null
                ? ""
                : message.replaceAll("[\\s，。！？,.!?]", "").toLowerCase(Locale.ROOT);
        if (CONFIRMATIONS.contains(normalized)) {
            return ConfirmationDecision.CONFIRM;
        }
        if (REJECTIONS.contains(normalized)) {
            return ConfirmationDecision.REJECT;
        }
        return ConfirmationDecision.UNKNOWN;
    }
}

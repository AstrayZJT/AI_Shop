package com.aishop.assistant.state;

public record ConditionEvaluation(boolean matched, String reason) {
    public static ConditionEvaluation success() {
        return new ConditionEvaluation(true, null);
    }

    public static ConditionEvaluation notMatched(String reason) {
        return new ConditionEvaluation(false, reason);
    }
}

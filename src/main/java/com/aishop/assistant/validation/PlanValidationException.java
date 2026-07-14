package com.aishop.assistant.validation;

import java.util.List;

public class PlanValidationException extends RuntimeException {

    private final List<String> violations;

    public PlanValidationException(List<String> violations) {
        super("计划校验失败: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}

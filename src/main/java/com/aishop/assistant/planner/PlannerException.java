package com.aishop.assistant.planner;

import com.aishop.assistant.model.PlannerFailureCode;

public class PlannerException extends RuntimeException {

    private final PlannerFailureCode code;
    private final String rawModelOutput;

    public PlannerException(PlannerFailureCode code, String message) {
        super(message);
        this.code = code;
        this.rawModelOutput = null;
    }

    public PlannerException(PlannerFailureCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.rawModelOutput = null;
    }

    public PlannerException(PlannerFailureCode code, String message, String rawModelOutput, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.rawModelOutput = rawModelOutput;
    }

    public PlannerFailureCode code() {
        return code;
    }

    public String rawModelOutput() {
        return rawModelOutput;
    }
}

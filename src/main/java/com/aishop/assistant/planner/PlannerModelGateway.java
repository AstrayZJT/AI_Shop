package com.aishop.assistant.planner;

public interface PlannerModelGateway {
    PlannerModelReply generatePlan(String systemPrompt, String userPrompt);
}

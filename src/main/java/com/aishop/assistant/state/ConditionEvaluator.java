package com.aishop.assistant.state;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.aishop.assistant.model.ConditionField;
import com.aishop.assistant.model.ConditionOperator;
import com.aishop.assistant.model.TaskCondition;
import com.aishop.assistant.tool.TaskToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ConditionEvaluator {

    private final ObjectMapper objectMapper;

    public ConditionEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ConditionEvaluation evaluate(List<TaskCondition> conditions,
                                        Map<String, TaskToolResult> completedResults) {
        for (TaskCondition condition : conditions) {
            TaskToolResult source = completedResults.get(condition.sourceTaskId());
            if (source == null) {
                return ConditionEvaluation.notMatched("条件来源任务不存在: " + condition.sourceTaskId());
            }
            JsonNode value = extractValue(condition.field(), objectMapper.valueToTree(source.data()));
            if (!matches(condition.operator(), condition.expectedValues(), value)) {
                return ConditionEvaluation.notMatched(
                        "条件不满足: " + condition.field() + " " + condition.operator());
            }
        }
        return ConditionEvaluation.success();
    }

    private JsonNode extractValue(ConditionField field, JsonNode data) {
        return switch (field) {
            case ORDER_STATUS -> firstPresent(
                    data.path("order").path("status"),
                    data.path("logistics").path("status"),
                    data.path("currentStatus"),
                    data.path("status"));
            case ORDER_SHIPPED_AT -> firstPresent(
                    data.path("order").path("shippedAt"),
                    data.path("logistics").path("shippedAt"),
                    data.path("shippedAt"));
        };
    }

    private JsonNode firstPresent(JsonNode... candidates) {
        for (JsonNode candidate : candidates) {
            if (!candidate.isMissingNode()) {
                return candidate;
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private boolean matches(ConditionOperator operator, List<String> expectedValues, JsonNode value) {
        boolean absent = value.isMissingNode() || value.isNull() || value.asText().isBlank();
        return switch (operator) {
            case IS_NULL -> absent;
            case NOT_NULL -> !absent;
            case EQ -> !absent && !expectedValues.isEmpty() && expectedValues.getFirst().equals(value.asText());
            case IN -> !absent && expectedValues.contains(value.asText());
        };
    }
}
